import akka.actor.{ActorRef, ActorSystem, Props, Actor, Inbox, PoisonPill}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import collection.mutable
import scala.math._
import akka.routing._

object Constants {
  val spikeDuration = 100 milliseconds
  val potentialHalfLife = 100 * spikeDuration
  val fireThreshold = 0.7
  val restPotential = 0.3
  val fireDelay = spikeDuration / 10
  val pruneThreshold = 10 seconds

  def hebbObservationInterval = spikeDuration / 10
  def hebbLearnInterval = spikeDuration * 10
  def strengthenWeight = 0.1

  assert(fireThreshold > restPotential)
}

import Constants._

object Messages {
  case class Strengthen(target: ActorRef)
  case object Fire
  case object Hebb // what fires together wires together
  case object HebbEvaluation
  case object Probe
  case class FireEvent(neuron: ActorRef, time: FiniteDuration)
  implicit val mypotentialOrdering = Ordering.by[FireEvent, FiniteDuration](-_.time)
  case object ImDead
  case object StayAlive //TODO: do that with constructor / inheritance
  case object IFollowYou
  case object AddSensor
  case object AddAction
  case object AddNeuron
  case class Signal(data: List[Double])

}

import Messages._

object Common {
  def localNow = System.nanoTime nanoseconds
  def globalNow = System.currentTimeMillis milliseconds
}
import Common._

object HelloAkkaScala extends App {

  val system = ActorSystem("serotonin")

  val network = system.actorOf(Props[Network])

  network ! AddSensor
  network ! AddSensor
  network ! AddAction
  network ! AddAction

  system.scheduler.schedule(0.seconds, 0.315.second, network, Signal(List(0.1, 0.2)))
  system.scheduler.schedule(0.seconds, 0.1654.second, network, Signal(List(0.35, 0.0)))

  Thread.sleep(10000)
  system.terminate()
}

class Network extends Actor {
  def newNeuron = context.actorOf(Props[Neuron])

  val sensors = mutable.ArrayBuffer.empty[ActorRef]
  val actions = mutable.ArrayBuffer.empty[ActorRef]
  val neurons = mutable.ArrayBuffer.empty[ActorRef]

  val fireData = mutable.PriorityQueue.empty[FireEvent]

  context.system.scheduler.schedule(0 seconds, hebbObservationInterval, self, Hebb)
  context.system.scheduler.schedule(0 seconds, hebbLearnInterval, self, HebbEvaluation)

  def receive = {
    case AddSensor =>
      val n = newNeuron
      n ! StayAlive
      sensors += n
      neurons += n

    case AddAction =>
      val n = newNeuron
      n ! StayAlive
      actions += n
      neurons += n

    case Signal(data) =>
      for ((sensor, d) <- sensors zip data) {
        sensor ! d
      }

    case Hebb =>
      if (neurons.size > 1)
        neurons(util.Random.nextInt.abs % neurons.size) ! Probe

    case HebbEvaluation =>
      println(s"learning from ${fireData.size} active points of ${neurons.size}")
      val maxInterval = spikeDuration * 1.5
      // we ensure that our observation window is large enough to avoid discarding possible connections
      if (fireData.nonEmpty) println(s"${fireData.size} > 1 && ${(fireData.min.time - fireData.max.time)} >= $maxInterval)")
      while (fireData.size > 1 && (fireData.min.time - fireData.max.time) >= maxInterval) {
        val earlier = fireData.dequeue
        val laters = fireData.takeWhile(later => (earlier.time - later.time) <= maxInterval)
        for (later <- laters if later != earlier) {
          earlier.neuron ! Strengthen(later.neuron)
        }
      }
      println(s"neurons: ${neurons.size}")

    case data: FireEvent =>
      fireData += data

    case ImDead =>
      neurons -= sender
      sensors -= sender
      actions -= sender
  }
}

class Neuron extends Actor {
  import context.{parent => network}

  val targets = mutable.HashMap.empty[ActorRef, Double].withDefaultValue(0.0)
  val predecessors = mutable.HashSet[ActorRef]()

  var lastReceivedSpike = localNow
  var lastFired = globalNow

  var _potential = restPotential
  def potential = {
    val timeDiff = localNow - lastReceivedSpike
    val factor = pow(0.5, timeDiff / potentialHalfLife)
    _potential * factor
  }

  def potential_=(newPotential: Double) {
    _potential = newPotential
  }

  var stayAlive = false

  def receive = {
    case spike: Double =>
      val high = potential + spike
      potential = high
      lastReceivedSpike = localNow
      if (high > fireThreshold)
        self ! Fire
    // println(s"\n-> ${self.path.name} Received spike $spike => $potential")

    case Fire =>
      targets.foreach {
        case (target, weight) =>
          context.system.scheduler.scheduleOnce(spikeDuration, target, weight)
      }
      potential = restPotential
      lastFired = globalNow
    // println(s"$potential => $w")
    // print(s".")

    case Strengthen(target) =>
      targets(target) = (targets(target) + strengthenWeight) min 1.0
      println(s"Strenghen: ${self.path.name} -[${targets(target)}]-> ${target.path.name}")
      sender ! IFollowYou

    case IFollowYou =>
      predecessors += sender

    case Probe =>
      if (!stayAlive && (localNow - lastReceivedSpike) > pruneThreshold) {
        println(s"pruned: ${self.path.name}")
        network ! ImDead
        predecessors.foreach { p => p ! ImDead }
        self ! PoisonPill
      } else
        sender ! FireEvent(self, globalNow)

    case ImDead =>
      targets -= sender

    case StayAlive =>
      stayAlive = true

    case unknown =>
      println(s"${self.path.name}: received unknown message: $unknown")
  }
}
