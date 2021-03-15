package serotonin

import scala.scalajs.js.JSApp
import scala.scalajs.js
import scala.scalajs.js.annotation._
import scala.scalajs.js.timers._
import org.scalajs.dom._
import js.JSConverters._

import d3v4._

import akka.actor._
import scala.concurrent.duration._
import collection.mutable
import vectory._

import actor.Messages._
import Constants._

object Main {
  def main(args:Array[String]): Unit {
    lazy val system = ActorSystem("serotonin")
    import system.dispatcher // Execution Context
    system.scheduler.scheduleOnce(0 seconds) {
      val dimensions = Vec2(400, 300)
      import dimensions.{width, height}
      val lightA = system.actorOf(Props(new Light("#A8F5FF", Vec2(width, height / 2 - 30))))
      val lightB = system.actorOf(Props(new Light("#FFEBA8", Vec2(width, height / 2 + 30))))
      val visualisation = new Visualization(dimensions)
      val network = system.actorOf(Props(new VisNetwork(visualisation)))

      network ! AddSensor(KeyboardSensorType("ArrowLeft"), Some(Vec2(50, height / 2 - 30)))
      network ! AddSensor(KeyboardSensorType("ArrowRight"), Some(Vec2(50, height / 2 + 30)))
      network ! AddMotor(lightA, Some(Vec2(width - 50, height / 2 - 30)))
      network ! AddMotor(lightB, Some(Vec2(width - 50, height / 2 + 30)))

      // system.scheduler.scheduleOnce(3.seconds, network, Signal(List(0.1, 0.2)))
      // system.scheduler.schedule(0.seconds, 0.315.second, network, Signal(List(0.1, 0.2)))
      // system.scheduler.schedule(0.seconds, 0.5654.second, network, Signal(List(0.35, 0.0)))
    }

  }
}
