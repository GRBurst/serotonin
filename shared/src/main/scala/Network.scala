package serotonin.actor

import concurrent.duration._
import concurrent.ExecutionContext.Implicits.global
import collection.{mutable, breakOut}
import math._
import akka.actor.{ActorRef, ActorSystem, Props, Actor, PoisonPill}
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import pharg._
import scala.unchecked

import serotonin.Constants._
import serotonin.Common._
import Messages._

trait Network extends Actor {
  def newNeuron(fireThreshold: Double = initialFireThreshold, stayAlive: Boolean = false, attachedMotor: Option[ActorRef] = None) = {
    context.actorOf(Props(new Neuron(fireThreshold, stayAlive, attachedMotor)))
  }

  val sensors = mutable.ArrayBuffer.empty[ActorRef]
  val actions = mutable.ArrayBuffer.empty[ActorRef]

  // var graph = DirectedGraphData[ActorRef, Double, Double](Set.empty, Set.empty, Map.empty, Map.empty)
  // def neurons = graph.vertices

  // val fireData = mutable.PriorityQueue.empty[FireEvent]

  // context.system.scheduler.schedule(0 seconds, hebbObservationInterval, self, Hebb)
  // context.system.scheduler.schedule(0 seconds, hebbLearnInterval, self, HebbEvaluation)

  val networkBehavior: Receive = {
    case AddSensor(sensorType) =>
      val n = newNeuron(stayAlive = true)
      sensors += n
      self ! AddedNeuron(n, initialFireThreshold)
      sensorType match {
        case KeyboardSensorType(key) => context.actorOf(Props(new KeyboardSensor(key, n)))
      }
    // graph += n

    case AddMotor(motor) =>
      val n = newNeuron(stayAlive = true, attachedMotor = Some(motor))
      actions += n
      self ! AddedNeuron(n, initialFireThreshold)
    // graph += n

    case AddNeuron(fireThreshold) =>
      val n = newNeuron(fireThreshold)
      sender ! n
      self ! AddedNeuron(n, fireThreshold)
    // graph += n

    case Signal(data) =>
      for ((sensor, d) <- sensors zip data) {
        sensor ! d
      }

    case ImDead =>
      // graph -= sender
      sensors -= sender
      actions -= sender

    case unknown =>
      println(s"unknown message: $unknown")

    // case data: FireEvent =>
    //   fireData += data

    // case Hebb =>
    //   if (neurons.size > 1)
    //     neurons(util.Random.nextInt.abs % neurons.size) ! Probe

    // case HebbEvaluation =>
    //   // println(s"learning from ${fireData.size} active points of ${neurons.size}")
    //   val maxInterval = spikeDuration * 1.5
    //   // we ensure that our observation window is large enough to avoid discarding possible connections
    //   // if (fireData.nonEmpty) println(s"${fireData.size} > 1 && ${(fireData.min.time - fireData.max.time)} >= $maxInterval)")
    //   while (fireData.size > 1 && (fireData.min.time - fireData.max.time) >= maxInterval) {
    //     val earlier = fireData.dequeue
    //     val laters = fireData.takeWhile(later => (earlier.time - later.time) <= maxInterval)
    //     for (later <- laters if later.neuron != earlier.neuron) {
    //       earlier.neuron ! Strengthen(later.neuron)
    //     }
    //   }
    //   println(s"neurons: ${neurons.size}")

  }
}
