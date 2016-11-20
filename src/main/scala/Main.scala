import akka.actor.{ActorRef, ActorSystem, Props, Actor, Inbox, PoisonPill}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import collection.mutable
import scala.math._
import akka.routing._

object Constants {
  val spikeDuration = 100 milliseconds
  val activationHalfLife = 100 * spikeDuration
  val fireThreshold = 0.7
  val deathThreshold = 0.001
  val initialActivation = 0.3
  val initialSpikeFactor = 1.0
  val fireDelay = spikeDuration / 10
  def activationFireDelay(activation: Double) = (fireDelay * (1.0 / (activation * activation))).asInstanceOf[FiniteDuration]
  def hebbWeight(earlierActivation: Double, laterActivation: Double) = 0.3
  def hebbObservationInterval = spikeDuration / 10
  def hebbLearnInterval = spikeDuration * 10
  def strengthenWeight = 0.1
  def sigmoid(x: Double) = tanh(x)

  assert(initialActivation > deathThreshold)
  assert(fireThreshold > deathThreshold)
  assert(fireThreshold > initialActivation)
}

import Constants._

object Messages {
  case class Strengthen(target: ActorRef)
  case object Fire
  case object Hebb // what fires together wires together
  case object HebbEvaluation
  case object GetActivation
  case class MyActivation(neuron: ActorRef, activation: Double, time: Long)
  implicit val myactivationOrdering = Ordering.by[MyActivation, Long](-_.time)
  case object ImDead
  case object StayAlive //TODO: do that with constructor / inheritance
  case object IFollowYou

  case object AddSensor
  case object AddAction
  case object AddNeuron
  case class Signal(data: List[Double])

}

import Messages._

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

  val activations = mutable.PriorityQueue.empty[MyActivation]

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
        neurons(util.Random.nextInt.abs % neurons.size) ! GetActivation

    case HebbEvaluation =>
      println(s"learning from ${activations.size} active points of ${neurons.size}")
      val maxInterval = spikeDuration * 1.5
      // we ensure that our observation window is large enough to avoid discarding possible connections
      if (activations.nonEmpty) println(s"${activations.size} > 1 && ${((activations.min.time - activations.max.time) milliseconds)} >= $maxInterval)")
      while (activations.size > 1 && ((activations.min.time - activations.max.time) milliseconds) >= maxInterval) {
        val earlier = activations.dequeue
        val laters = activations.takeWhile(later => ((earlier.time - later.time) milliseconds) <= maxInterval)
        for (later <- laters if later != earlier) {
          earlier.neuron ! Strengthen(later.neuron)
        }
      }
      println(s"neurons: ${neurons.size}")

    case a: MyActivation =>
      activations += a

    case ImDead =>
      neurons -= sender
      sensors -= sender
      actions -= sender
  }
}

class Neuron extends Actor {
  def now = System.nanoTime

  val targets = mutable.HashMap.empty[ActorRef, Double].withDefaultValue(0.0)
  val predecessors = mutable.HashSet[ActorRef]()

  var lastReceivedSpike = now
  var _activation = initialActivation
  def activation = {
    val time = now
    val timeDiff = (time - lastReceivedSpike) nanoseconds
    val factor = pow(0.5, timeDiff / activationHalfLife)
    val value = _activation * factor

    if (!stayAlive && value < deathThreshold) {
      println("killed")
      context.parent ! ImDead
      predecessors.foreach { p => p ! ImDead }
      self ! PoisonPill
    }

    value
  }

  def activation_=(newActivation: Double) {
    _activation = newActivation
  }

  var spikeFactor = initialSpikeFactor
  var stayAlive = false

  context.system.scheduler.scheduleOnce((1 / activation) seconds, self, Fire)

  def receive = {
    case spike: Double =>
      activation = sigmoid(activation + spike * spikeFactor)
      lastReceivedSpike = now
    // println(s"\n-> ${self.path.name} Received spike $spike => $activation")
    case Fire =>
      val a = activation
      if (a > fireThreshold) {
        targets.foreach {
          case (target, weight) =>
            context.system.scheduler.scheduleOnce(spikeDuration, target, weight)
        }
        context.system.scheduler.scheduleOnce(activationFireDelay(a), self, Fire)
        // println(s"$activation => $w")
        // print(s".")
      }

    case Strengthen(target) =>
      targets(target) = sigmoid(targets(target) + strengthenWeight)
      println(s"Strenghen: ${self.path.name} -[${targets(target)}]-> ${target.path.name}")
      sender ! IFollowYou

    case IFollowYou =>
      predecessors += sender

    case GetActivation =>
      if (activation > fireThreshold)
        sender ! MyActivation(self, activation, System.currentTimeMillis)

    case ImDead =>
      targets -= sender

    case StayAlive =>
      stayAlive = true

    case unknown =>
      println(s"${self.path.name}: received unknown message: $unknown")
  }
}
