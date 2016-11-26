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

class VisNetwork(visualization: Visualization) extends Actor with actor.Network {
  var neurons = mutable.HashMap[String, Neuron]()
  var synapses = mutable.HashMap[(String, String), Synapse]()
  def updateGraphViz() {
    visualization.updateGraph(neurons.values.toJSArray, synapses.values.toJSArray)
  }

  def receive: Receive = ({

    case AddedNeuron(neuron, fireThreshold) =>
      val id = neuron.path.name
      neurons += (id -> new Neuron(id, fireThreshold, restPotential, visualization.width / 2, visualization.height / 2))

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
