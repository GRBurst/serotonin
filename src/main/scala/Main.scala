import akka.actor.{ ActorRef, ActorSystem, Props, Actor, Inbox, PoisonPill }
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import collection.mutable
import scala.math._
import akka.routing._

object Constants {
  val decay = 0.5
  implicit val myactivationOrdering = Ordering.by[MyActivation, Long](_.time)
}

import Constants._

object HelloAkkaScala extends App {

  val system = ActorSystem("helloakka")

  val network = system.actorOf(Props[Network])

  network ! AddSensor
  network ! AddSensor
  network ! AddAction
  network ! AddAction

  system.scheduler.schedule(0.seconds, 0.315.second, network, Signal(List(0.1, 0.2)))
  system.scheduler.schedule(0.seconds, 0.1654.second, network, Signal(List(0.35, 0.0)))

  // Thread.sleep(20000)
  // system.shutdown()
}

case class Synapse(child: ActorRef, weight: Double)
case object Fire
case object Hebb // what fires together wires together
case object HebbEvaluation
case object GetActivation
case class MyActivation(neuron: ActorRef, activation: Double, time: Long)
case object ImDead
case object StayAlive //TODO: do that with constructor / inheritance
case object IFollowYou

case object AddSensor
case object AddAction
case object AddNeuron
case class Signal(data: List[Double])

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


    case Hebb =>
      if (neurons.size > 1)
        neurons(util.Random.nextInt.abs % neurons.size) ! GetActivation

    case HebbEvaluation =>
      val maxInterval = 200
      while (activations.size > 1 && activations.max.time - activations.min.time >= maxInterval) {
        val current = activations.dequeue // jünge
        val others = activations.takeWhile(a => current.time - a.time < maxInterval)
        for (other <- others) {
          val n = context.actorOf(Props[Neuron])
          neurons += n
          current.neuron ! Synapse(n, 0.2)
          n ! Synapse(other.neuron, 0.2)
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

  var activation = 0.4
  var spikeFactor = 0.4
  var lastReceivedSpike = now
  var stayAlive = false

  def applyDecay() {
    val time = now
    val timeDiff = time - lastReceivedSpike
    val factor = pow(decay, timeDiff / 1000000000.0)
    lastReceivedSpike = time
    activation *= factor

    if (!stayAlive && activation < 0.01) {
      println("killed")
      context.parent ! ImDead
      predecessors.foreach { p => p ! ImDead }
      self ! PoisonPill
    }
  }

  context.system.scheduler.scheduleOnce((1 / activation) seconds, self, Fire)

  def receive = {
    case spike: Double =>
      applyDecay()
      activation = (activation + spike * spikeFactor) min 1.0
    // println(s"\n-> ${self.path.name} Received spike $spike => $activation")
    case Fire =>
      applyDecay()
      followers.foreach { case Synapse(child, weight) => child ! weight }
      val w = 1 / (activation * activation)
      if (w < 20000)
        context.system.scheduler.scheduleOnce(w milliseconds, self, Fire)
    // println(s"$activation => $w")
    // print(s".")
    case s: Synapse =>
      followers += s
      sender ! IFollowYou
    case IFollowYou =>
      predecessors += sender

    case GetActivation =>
      if (activation > 0.7)
        sender ! MyActivation(self, activation, System.currentTimeMillis)

    case ImDead =>
      followers --= followers.find(_.child == sender)

    case StayAlive =>
      stayAlive = true

    case unknown =>
      println(s"${self.path.name}: received unknown message: $unknown")
  }
}
