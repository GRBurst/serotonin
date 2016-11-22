import concurrent.duration._
import concurrent.ExecutionContext.Implicits.global
import collection.{mutable, breakOut}
import math._
import akka.actor.{ActorRef, ActorSystem, Props, Actor, PoisonPill}
import akka.pattern.{ask,pipe}
import akka.util.Timeout
import pharg._
import scala.unchecked

object Constants {
  val spikeDuration = 100 milliseconds
  val potentialHalfLife = 100 * spikeDuration
  val initialFireThreshold = 0.7
  val restPotential = 0.3
  val fireDelay = spikeDuration / 10
  val pruneThreshold = 10 seconds
  def lowerFireThreshold(threshold:Double) = threshold*0.9
  def higherFireThreshold(threshold:Double) = threshold*1.374

  def hebbObservationInterval = spikeDuration / 10
  def hebbLearnInterval = spikeDuration * 10
  def strengthenWeight = 0.1

  implicit val timeout = Timeout(60 seconds)

  assert(initialFireThreshold > restPotential)
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
  case object IFollowYou
  case object AddSensor
  case object AddAction
  case class AddNeuron(fireThreshold: Double)
  case class Signal(data: List[Double])
  case object Graph
  case object DumpGraph
}

import Messages._

object Common {
  def localNow = System.nanoTime nanoseconds
  def globalNow = System.currentTimeMillis milliseconds
}
import Common._

object Serotonin extends App {

  val system = ActorSystem("serotonin")

  val network = system.actorOf(Props[Network])

  network ! AddSensor
  network ! AddSensor
  network ! AddAction
  network ! AddAction

  system.scheduler.schedule(0.seconds, 0.315.second, network, Signal(List(0.1, 0.2)))
  system.scheduler.schedule(0.seconds, 0.1654.second, network, Signal(List(0.35, 0.0)))

  Thread.sleep(20000)
  system.terminate()
}

class Network extends Actor {
  def newNeuron(fireThreshold: Double = initialFireThreshold, stayAlive: Boolean = false) = context.actorOf(Props(new Neuron(fireThreshold, stayAlive)))

  val sensors = mutable.ArrayBuffer.empty[ActorRef]
  val actions = mutable.ArrayBuffer.empty[ActorRef]
  val neurons = mutable.ArrayBuffer.empty[ActorRef]

  var graph = DirectedGraphData[ActorRef,Double,Double](Set.empty, Set.empty, Map.empty, Map.empty)

  val fireData = mutable.PriorityQueue.empty[FireEvent]

  // context.system.scheduler.schedule(0 seconds, hebbObservationInterval, self, Hebb)
  // context.system.scheduler.schedule(0 seconds, hebbLearnInterval, self, HebbEvaluation)

  // context.system.scheduler.schedule(5 seconds, 10 seconds, self, Graph)
  // context.system.scheduler.schedule(14 seconds, 10 seconds, self, DumpGraph)
  context.system.scheduler.scheduleOnce(5 seconds, self, Graph)
  context.system.scheduler.scheduleOnce(14 seconds, self, DumpGraph)

  def receive = {
    case AddSensor =>
      val n = newNeuron(stayAlive = true)
      sensors += n
      neurons += n

    case AddAction =>
      val n = newNeuron(stayAlive = true)
      actions += n
      neurons += n

    case AddNeuron(fireThreshold) =>
      val n = newNeuron(fireThreshold)
      neurons += n
      sender ! n

    case Signal(data) =>
      for ((sensor, d) <- sensors zip data) {
        sensor ! d
      }

    case Hebb =>
      if (neurons.size > 1)
        neurons(util.Random.nextInt.abs % neurons.size) ! Probe

    case HebbEvaluation =>
      // println(s"learning from ${fireData.size} active points of ${neurons.size}")
      val maxInterval = spikeDuration * 1.5
      // we ensure that our observation window is large enough to avoid discarding possible connections
      // if (fireData.nonEmpty) println(s"${fireData.size} > 1 && ${(fireData.min.time - fireData.max.time)} >= $maxInterval)")
      while (fireData.size > 1 && (fireData.min.time - fireData.max.time) >= maxInterval) {
        val earlier = fireData.dequeue
        val laters = fireData.takeWhile(later => (earlier.time - later.time) <= maxInterval)
        for (later <- laters if later.neuron != earlier.neuron) {
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

    case Graph =>
      graph = DirectedGraphData[ActorRef,Double,Double](neurons.toSet, Set.empty, Map.empty, Map.empty)
      neurons.foreach{n => n ! Graph}

    case DumpGraph =>
      // ${neurons.map {n => s"${n.path.name}"}}
      val dot = s"""
        digraph network {
          ${(sensors.map {n => s"""${n.path.name.tail}[shape = circle, rank = "source", style = filled, fillcolor = "#FFAB38"];"""}).mkString}
          ${(actions.map {n => s"""${n.path.name.tail}[shape = circle, rank = "sink", style = filled, fillcolor = "#02E8D5"];"""}).mkString}
          ${((neurons -- actions -- sensors).map {n => s"${n.path.name.tail}[shape = circle];"}).mkString}
          ${(graph.edges map { case (e: Edge[ActorRef] @unchecked) =>
            s"${e.in.path.name.tail} -> ${e.out.path.name.tail};"
          }).mkString
          }
        }
        """
        val p = new java.io.PrintWriter("/tmp/sero_graph.dot")
        p.write(dot)
        p.close()

    case e: List[Edge[ActorRef]] @unchecked =>
      graph ++= e

      }
}

class Neuron(var fireThreshold: Double, stayAlive: Boolean) extends Actor {
  import context.{parent => network}

  val targets = mutable.HashMap.empty[ActorRef, Double].withDefaultValue(0.0)
  val sources = mutable.HashSet[ActorRef]()

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

  def receive = {
    case spike: Double =>
      val high = potential + spike
      potential = high
      lastReceivedSpike = localNow
      if (high > fireThreshold)
        self ! Fire
    // println(s"\n-> ${self.path.name} Received spike $spike => $potential")

    case Fire =>
      if(targets.isEmpty) {
        (network ? AddNeuron(higherFireThreshold(fireThreshold))).mapTo[ActorRef].map ( n =>          Strengthen(n)
          ).pipeTo(self)
      } else {
        targets.foreach {
          case (target, weight) =>
            context.system.scheduler.scheduleOnce(spikeDuration, target, weight)
        }
      }
      potential = restPotential
      lastFired = globalNow
      fireThreshold = lowerFireThreshold(fireThreshold)
    // println(s"$potential => $w")
    // print(s".")

    case Strengthen(target) =>
      targets(target) = (targets(target) + strengthenWeight) min 1.0
      // println(s"Strenghen: ${self.path.name} -[${targets(target)}]-> ${target.path.name}")
      sender ! IFollowYou

    case IFollowYou =>
      sources += sender

    case Probe =>
      if (!stayAlive && (localNow - lastReceivedSpike) > pruneThreshold) {
        println(s"pruned: ${self.path.name}")
        network ! ImDead
        sources.foreach { p => p ! ImDead }
        self ! PoisonPill
      } else
        sender ! FireEvent(self, globalNow)

    case ImDead =>
      targets -= sender

    case Graph =>
      sender ! targets.map{case (n,w) => Edge(self,n)}(breakOut[mutable.Map[ActorRef, Double], Edge[ActorRef], List[Edge[ActorRef]]])

    case unknown =>
      println(s"${self.path.name}: received unknown message: $unknown")
  }
}
