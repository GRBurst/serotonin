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

import actor.Messages._
import Constants._

@ScalaJSDefined
class Neuron(
  val id: String,
  var fireThreshold: Double,
  var potential: Double,
  var x: js.UndefOr[Double] = js.undefined,
  var y: js.UndefOr[Double] = js.undefined
) extends js.Object {
  override def hashCode = id.hashCode

  def canEqual(a: Any) = a.isInstanceOf[Neuron]

  override def equals(that: Any): Boolean =
    that match {
      case that: Neuron => that.canEqual(this) && this.id == that.id
      case _ => false
    }
}
case class Synapse(source: Neuron, target: Neuron, weight: Double) extends d3js.Link[Neuron]

class Visualization {
  val dimensions @ (width, height) = (400.0, 300.0)
  val svg = d3.select("#container")
    .append("svg")
    .attr("width", width)
    .attr("height", height)

  var synapseLines = svg.append("g").selectAll("line").data(js.Array[Synapse]())
  var neuronCircles = svg.append("g").selectAll("circle").data(js.Array[Neuron]())
  val spikeGroup = svg.append("g")

  val force = d3.layout.force[Neuron, Synapse]()
    .size(dimensions)
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
  def updateGraph(nodes: js.Array[Neuron], links: js.Array[Synapse]) {
    synapseLines = synapseLines.data(links)
    neuronCircles = neuronCircles.data(nodes, (n: Neuron) => n.id)

    synapseLines.enter()
      .append("line")
      .attr("stroke", "gray")
      .attr("stroke-width", (d: Synapse) => d.weight * 3)

    neuronCircles.enter()
      .append("circle")

    neuronCircles
      .attr("r", 5)
      .style("opacity", (d: Neuron) => 1.0 / d.fireThreshold)
      .attr("fill", (d: Neuron) => potentialColor(d.potential / d.fireThreshold))
      .attr("stroke", "black")
      .attr("stroke-width", 1)
      .attr("cx", (d: Neuron) => d.x)
      .attr("cy", (d: Neuron) => d.y)

    force.nodes(nodes).links(links)
    force.start()
  }

  def sendSpike(source: Neuron, target: Neuron, strength: Double) {
    spikeGroup
      .append("circle")
      .attr("r", strength * 30)
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

object Main extends JSApp {
  def main() {
    lazy val system = ActorSystem("serotonin")
    import system.dispatcher // Execution Context
    system.scheduler.scheduleOnce(0 seconds) {
      val viz = new Visualization()
      val network = system.actorOf(Props(new VisNetwork(viz)))

      network ! AddSensor
      network ! AddSensor
      network ! AddAction
      network ! AddAction

      // system.scheduler.scheduleOnce(3.seconds, network, Signal(List(0.1, 0.2)))
      system.scheduler.schedule(0.seconds, 0.315.second, network, Signal(List(0.1, 0.2)))
      system.scheduler.schedule(0.seconds, 0.5654.second, network, Signal(List(0.35, 0.0)))
    }

  }
}

class VisNetwork(visualization: Visualization) extends Actor with actor.Network {
  var neurons = mutable.HashMap[String, Neuron]()
  var synapses = mutable.HashMap[(String, String), Synapse]()
  def updateGraphViz() {
    visualization.updateGraph(neurons.values.toJSArray, synapses.values.toJSArray)
  }

  def receive: Receive = ({

    case AddedNeuron(neuron, fireThreshold) =>
      val id = neuron.path.name
      neurons += (id -> new Neuron(id, fireThreshold, restPotential))

    case AddedSynapse(a, b, weight) =>
      val aId = a.path.name
      val bId = b.path.name
      synapses += ((aId -> bId) -> new Synapse(neurons(aId), neurons(bId), weight))
      updateGraphViz()

    case Spike(a, b, weight) =>
      visualization.sendSpike(neurons(a.path.name), neurons(b.path.name), weight)

    case UpdatedFireThreshold(neuron, threshold) =>
      neurons(neuron.path.name).fireThreshold = threshold
      updateGraphViz()

    case UpdatedPotential(neuron, potential) =>
      neurons(neuron.path.name).potential = potential
      updateGraphViz()

  }: Receive) orElse networkBehavior
}
