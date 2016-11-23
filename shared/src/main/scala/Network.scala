package serotonin

import concurrent.duration._
import concurrent.ExecutionContext.Implicits.global
import collection.{mutable, breakOut}
import math._
import akka.actor.{ActorRef, ActorSystem, Props, Actor, PoisonPill}
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import pharg._
import scala.unchecked

import Constants._
import Messages._
import Common._

class Network extends Actor {
  def newNeuron(fireThreshold: Double = initialFireThreshold, stayAlive: Boolean = false) = context.actorOf(Props(new Neuron(fireThreshold, stayAlive)))

  val sensors = mutable.ArrayBuffer.empty[ActorRef]
  val actions = mutable.ArrayBuffer.empty[ActorRef]
  val neurons = mutable.ArrayBuffer.empty[ActorRef]

  var graph = DirectedGraphData[ActorRef, Double, Double](Set.empty, Set.empty, Map.empty, Map.empty)

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
      graph = DirectedGraphData[ActorRef, Double, Double](neurons.toSet, Set.empty, Map.empty, Map.empty)
      neurons.foreach { n => n ! Graph }

    case DumpGraph =>
      // ${neurons.map {n => s"${n.path.name}"}}
      val dot = s"""
        digraph network {
          ${(sensors.map { n => s"""${n.path.name.tail}[shape = circle, rank = "source", style = filled, fillcolor = "#FFAB38"];""" }).mkString}
          ${(actions.map { n => s"""${n.path.name.tail}[shape = circle, rank = "sink", style = filled, fillcolor = "#02E8D5"];""" }).mkString}
          ${((neurons -- actions -- sensors).map { n => s"${n.path.name.tail}[shape = circle];" }).mkString}
          ${
        (graph.edges map {
          case (e: Edge[ActorRef] @unchecked) =>
            s"${e.in.path.name.tail} -> ${e.out.path.name.tail};"
        }).mkString
      }
        }
        """
      val p = new java.io.PrintWriter("/tmp/sero_graph.dot")
      p.write(dot)
      p.close()

    case e: List[Edge[ActorRef]] @unchecked =>
    // graph ++= e

  }
}
