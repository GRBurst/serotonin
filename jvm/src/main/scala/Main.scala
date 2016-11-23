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
