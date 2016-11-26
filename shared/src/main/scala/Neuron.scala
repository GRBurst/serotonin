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

class Neuron(var fireThreshold: Double, stayAlive: Boolean, attachedMotor: Option[ActorRef] = None) extends Actor {
  import context.{parent => network}

  val targets = mutable.HashMap.empty[ActorRef, Double].withDefaultValue(0.0)
  val sources = mutable.HashSet[ActorRef]()
  var hasTargets = false

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
    network ! UpdatedPotential(self, newPotential)
  }

  if (sources.isEmpty && attachedMotor.isDefined)
    self ! Fire

  def receive = {
    case spike: Double =>
      val newPotential = potential + spike
      potential = newPotential
      lastReceivedSpike = localNow
      if (newPotential > fireThreshold + restPotential)
        self ! Fire
    // println(s"\n-> ${self.path.name} Received spike $spike => $potential")

    case Fire =>
      if (targets.isEmpty && hasTargets == false) {
        hasTargets = true // avoid creation of more than one neuron
        (network ? AddNeuron(higherFireThreshold(fireThreshold))).mapTo[ActorRef].map(n => Strengthen(n)).pipeTo(self)
      } else {
        targets.foreach {
          case (target, weight) =>
            network ! Spike(self, target, weight)
            context.system.scheduler.scheduleOnce(spikeDuration, target, weight)
        }
      }
      attachedMotor.foreach { motor =>
        if (sources.isEmpty)
          context.system.scheduler.scheduleOnce(util.Random.nextInt(4000) milliseconds, self, Fire)

        motor ! Fire
      }
      potential = 0 //restPotential
      lastFired = globalNow
      fireThreshold = lowerFireThreshold(fireThreshold)
      network ! UpdatedFireThreshold(self, fireThreshold)
    // println(s"$potential => $w")
    // print(s".")

    case Strengthen(target) =>
      // strenghen self -> target
      // self.targets += target
      // target.sources += self
      if (target == self) {
        println("WARNING: target == self")
      }
      if (sender == self) {
        println("WARNING: sender == self")
      }
      val newWeight = (targets(target) + strengthenWeight) min 1.0
      if (targets.get(target).isEmpty) network ! AddedSynapse(self, target, strengthenWeight)
      else network ! UpdatedSynapseWeight(self, target, newWeight)
      targets(target) = newWeight
      hasTargets = true
      // println(s"Strenghen: ${self.path.name} -[${targets(target)}]-> ${target.path.name}")
      target ! IAmSourceOfYou

    case IAmSourceOfYou =>
      // println(s"($sender) is source of me ($self)")
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
