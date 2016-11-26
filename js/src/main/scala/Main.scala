package serotonin

import scala.scalajs.js.JSApp
import scala.scalajs.js
import scala.scalajs.js.annotation._
import scala.scalajs.js.timers._
import org.scalajs.dom._
import js.JSConverters._

import org.singlespaced.d3js.Ops._
import org.singlespaced.d3js.d3
import org.singlespaced.d3js

import akka.actor._
import scala.concurrent.duration._
import collection.mutable

import actor.Messages._
import Constants._

object Main extends JSApp {
  def main() {
    lazy val system = ActorSystem("serotonin")
    import system.dispatcher // Execution Context
    system.scheduler.scheduleOnce(0 seconds) {
      val lightA = system.actorOf(Props(new Light("#A8F5FF")))
      val lightB = system.actorOf(Props(new Light("#FFEBA8")))
      val viz = new Visualization()
      val network = system.actorOf(Props(new VisNetwork(viz)))

      network ! AddSensor(KeyboardSensorType("ArrowLeft"))
      network ! AddSensor(KeyboardSensorType("ArrowRight"))
      network ! AddMotor(lightA)
      network ! AddMotor(lightB)

      // system.scheduler.scheduleOnce(3.seconds, network, Signal(List(0.1, 0.2)))
      // system.scheduler.schedule(0.seconds, 0.315.second, network, Signal(List(0.1, 0.2)))
      // system.scheduler.schedule(0.seconds, 0.5654.second, network, Signal(List(0.35, 0.0)))
    }

  }
}
