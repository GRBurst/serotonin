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

object Constants {
  val spikeDuration = 300 milliseconds
  val potentialHalfLife = 100 * spikeDuration
  val initialFireThreshold = 0.7
  val restPotential = 0.3
  val fireDelay = spikeDuration / 10
  val pruneThreshold = 10 seconds
  def lowerFireThreshold(threshold: Double) = threshold * 0.95
  def higherFireThreshold(threshold: Double) = threshold * 1.3

  def hebbObservationInterval = spikeDuration / 10
  def hebbLearnInterval = spikeDuration * 10
  def strengthenWeight = 0.1

  implicit val timeout = Timeout(60 seconds)

  assert(initialFireThreshold > restPotential)
}
