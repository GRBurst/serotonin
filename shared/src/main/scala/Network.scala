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

  val spikeData = mutable.PriorityQueue.empty[SpikeEvent]

  val networkBehavior: Receive = {
    case Spike(a, b, weight) =>
      val interval = spikeDuration * 1.5
      spikeData += SpikeEvent(b, globalNow)
      if (spikeData.size > 1 && spikeData.min.time - spikeData.max.time > interval) {
        while (spikeData.size > 1 && (spikeData.min.time - spikeData.max.time) > interval) {
          val earlier = spikeData.dequeue
          val laters = spikeData.takeWhile(later => (earlier.time - later.time) <= interval)
          val candidates = laters.filter(_.neuron != earlier.neuron).groupBy(_.neuron)
          for ((later, spikes) <- candidates) {
            if (spikes.size > 10)
              earlier.neuron ! Strengthen(later)
          }
        }
      }

    case AddSensor(sensorType, fixedPos) =>
      val n = newNeuron(stayAlive = true)
      sensors += n
      self ! AddedNeuron(n, initialFireThreshold, fixedPos)
      sensorType match {
        case KeyboardSensorType(key) => context.actorOf(Props(new KeyboardSensor(key, n)))
      }

    case AddMotor(motor, fixedPos) =>
      val n = newNeuron(stayAlive = true, attachedMotor = Some(motor))
      actions += n
      self ! AddedNeuron(n, initialFireThreshold, fixedPos)

    case AddNeuron(fireThreshold) =>
      val n = newNeuron(fireThreshold)
      sender ! n
      self ! AddedNeuron(n, fireThreshold)

    case Signal(data) =>
      for ((sensor, d) <- sensors zip data) {
        sensor ! d
      }

    // TODO: when to trigger this?
    case ImDead =>
      sensors -= sender
      actions -= sender

    case unknown =>
      println(s"unknown message: $unknown")

  }
}
