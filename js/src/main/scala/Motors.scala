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

class Light(color: String) extends Actor {
  val bgColor = "#222222"
  val light = d3.select("#container").append("div")
  light
    .style("display", "inline-block")
    .style("margin", "2px")
    .style("border", "1px solid black")
    .style("width", "50px")
    .style("height", "50px")
    .style("background", bgColor)

  def receive = {
    case Fire =>
      light
        .style("background", color)
        .transition().duration(0).delay(200)
        .style("background", bgColor)
  }
}
