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

  assert(initialActivation > deathThreshold)
  assert(fireThreshold > deathThreshold)
  assert(fireThreshold > initialActivation)
}

import Constants._

object Messages {
  case class Synapse(child: ActorRef, weight: Double)
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
}

class Network extends Actor {
  def newNeuron = context.actorOf(Props[Neuron])

  val sensors = mutable.ArrayBuffer.empty[ActorRef]
  val actions = mutable.ArrayBuffer.empty[ActorRef]
  val neurons = mutable.ArrayBuffer.empty[ActorRef]

  val activations = mutable.PriorityQueue.empty[MyActivation]

  context.system.scheduler.schedule(0 seconds, 0.1 second, self, Hebb)
  context.system.scheduler.schedule(0 seconds, 1.0 second, self, HebbEvaluation)

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
      val maxInterval = spikeDuration * 1.5
      while(activations.size > 1 && ((activations.max.time - activations.min.time) nanoseconds) <= maxInterval) {
        val earlier = activations.dequeue // oldest
        val laters = activations.takeWhile(later => ((earlier.time - later.time) nanoseconds) <= maxInterval)
        for (later <- laters) {
          val timeDiff = (later.time - earlier.time) nanoseconds
          if( timeDiff < spikeDuration * 1.5 ) {
            earlier.neuron ! Synapse(later.neuron, hebbWeight(earlier.activation, later.activation))
          } else { // (spikeDuration * 1.5) <= timeDiff <= (spikeDuration * 2.5)
            val n = context.actorOf(Props[Neuron])
            neurons += n
            earlier.neuron ! Synapse(n, 0.2)
            n ! Synapse(later.neuron, 0.2)
          }
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

  val followers = mutable.ArrayBuffer[Synapse]()
  val predecessors = mutable.ArrayBuffer[ActorRef]()

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
      activation = (activation + spike * spikeFactor) min 1.0
      lastReceivedSpike = now
    // println(s"\n-> ${self.path.name} Received spike $spike => $activation")
    case Fire =>
      val a = activation
      if (a > fireThreshold) {
        followers.foreach {
          case Synapse(child, weight) =>
            context.system.scheduler.scheduleOnce(spikeDuration, child, weight)
        }
        context.system.scheduler.scheduleOnce(activationFireDelay(a), self, Fire)
        // println(s"$activation => $w")
        // print(s".")
      }

    case s: Synapse =>
      followers += s
      sender ! IFollowYou
    case IFollowYou =>
      predecessors += sender

    case GetActivation =>
      if (activation > fireThreshold)
        sender ! MyActivation(self, activation, System.currentTimeMillis)

    case ImDead =>
      followers --= followers.find(_.child == sender)

    case StayAlive =>
      stayAlive = true

    case unknown =>
      println(s"${self.path.name}: received unknown message: $unknown")
  }
}
