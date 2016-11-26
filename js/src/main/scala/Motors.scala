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
import actor.Messages._
import vectory._

class Light(color: String, pos: Vec2) extends Actor {
  val bgColor = "#222222"
  val border = 1
  val size = Vec2(50, 50) + (2 * border)
  val cornerpos = pos - size / 2
  val light = d3.select("#container").append("div")
  light
    .style("position", "absolute")
    .style("left", s"${cornerpos.x}px")
    .style("top", s"${cornerpos.y}px")
    .style("margin", "0px")
    .style("border", s"${border}px solid black")
    .style("width", s"${size.width}px")
    .style("height", s"${size.height}px")
    .style("background", bgColor)

  def receive = {
    case Fire =>
      light
        .style("background", color)
        .transition().duration(0).delay(200)
        .style("background", bgColor)
  }
}
