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
      if (targets.isEmpty) {
        (network ? AddNeuron(higherFireThreshold(fireThreshold))).mapTo[ActorRef].map(n => Strengthen(n)).pipeTo(self)
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
      sender ! targets.map { case (n, w) => Edge(self, n) }(breakOut[mutable.Map[ActorRef, Double], Edge[ActorRef], List[Edge[ActorRef]]])

    case unknown =>
      println(s"${self.path.name}: received unknown message: $unknown")
  }
}
