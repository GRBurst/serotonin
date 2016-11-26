package serotonin

import scala.scalajs.js.JSApp
import scala.scalajs.js
import scala.scalajs.js.annotation._
import scala.scalajs.js.timers._
import org.scalajs.dom.Event
import js.JSConverters._

import org.singlespaced.d3js.Ops._
import org.singlespaced.d3js.d3
import org.singlespaced.d3js

import akka.actor._
import scala.concurrent.duration._
import collection.mutable
import math._
import vectory._

import Constants._

@ScalaJSDefined
class Neuron(
  val id: String,
  var fireThreshold: Double,
  var potential: Double,
  var x: js.UndefOr[Double] = js.undefined,
  var y: js.UndefOr[Double] = js.undefined,
  val fixedPos: js.UndefOr[Vec2] = js.undefined
) extends js.Object {
  val fixed = fixedPos.isDefined
  fixedPos.foreach {
    case Vec2(fx, fy) =>
      x = fx
      y = fy
  }

  def canEqual(a: Any) = a.isInstanceOf[Neuron]
  override def equals(that: Any): Boolean = that match {
    case that: Neuron => that.canEqual(this) && this.id == that.id
    case _ => false
  }
  override def hashCode = id.hashCode
}
case class Synapse(source: Neuron, target: Neuron, var weight: Double) extends d3js.Link[Neuron]

class Visualization(val dimensions: Vec2 = Vec2(400, 300)) {
  import dimensions.{width, height}
  val svg = d3.select("#container")
    .append("svg")
    .attr("width", width)
    .attr("height", height)

  var synapseLines = svg.append("g").selectAll("line").data(js.Array[Synapse]())
  var neuronCircles = svg.append("g").selectAll("circle").data(js.Array[Neuron]())
  val spikeGroup = svg.append("g")

  val force = d3.layout.force[Neuron, Synapse]()
    .size(dimensions.toTuple)
    .linkDistance(30)
    .charge(-60)

  force.on("tick", { event =>
    synapseLines
      .attr("x1", (d: Synapse) => d.source.x)
      .attr("y1", (d: Synapse) => d.source.y)
      .attr("x2", (d: Synapse) => d.target.x)
      .attr("y2", (d: Synapse) => d.target.y)

    neuronCircles
      .attr("cx", (d: Neuron) => d.x)
      .attr("cy", (d: Neuron) => d.y)
  })

  def potentialColor = d3.interpolateRgb("#7DBBFF", "#FFE77D")
  def updateGraphTopology(nodes: js.Array[Neuron], links: js.Array[Synapse]) {
    println("updateGraph")
    synapseLines = synapseLines.data(links)
    neuronCircles = neuronCircles.data(nodes, (n: Neuron) => n.id)

    synapseLines.enter()
      .append("line")
      .attr("stroke", "gray")

    neuronCircles.enter()
      .append("circle")
      .attr("stroke", "black")
      .attr("stroke-width", 1)

    updateGraphAppearance()

    force.nodes(nodes).links(links)
    force.start()
  }

  def updateGraphAppearance() {
    neuronCircles
      .attr("r", (d: Neuron) => 1.0 / sqrt(restPotential + d.fireThreshold) * 5)
      // .style("opacity", (d: Neuron) => 1.0 / d.fireThreshold)
      .attr("fill", (d: Neuron) => potentialColor(d.potential / (restPotential + d.fireThreshold)))
      .attr("cx", (d: Neuron) => d.x)
      .attr("cy", (d: Neuron) => d.y)

    synapseLines
      .attr("stroke-width", (d: Synapse) => d.weight * 3)
  }

  def visualizeSpike(source: Neuron, target: Neuron, strength: Double) {
    spikeGroup
      .append("circle")
      .attr("r", 3 + strength * 3)
      .attr("fill", "#FFE77D")
      .attr("stroke", "black")
      .attr("stroke-width", "0.5")
      .attr("cx", source.x.get)
      .attr("cy", source.y.get)
      .transition().duration(spikeDuration.toMillis)
      .attr("cx", target.x.get)
      .attr("cy", target.y.get)
      .remove()
  }
}
