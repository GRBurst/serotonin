package serotonin

import scala.scalajs.js.JSApp
import scala.scalajs.js
import scala.scalajs.js.annotation._
import scala.scalajs.js.timers._
import js.JSConverters._

import org.singlespaced.d3js.Ops._
import org.singlespaced.d3js.d3
import org.singlespaced.d3js

@ScalaJSDefined
class Node(var x: js.UndefOr[Double] = js.undefined, var y: js.UndefOr[Double] = js.undefined) extends js.Object
class Link(val source: Node, val target: Node) extends d3js.Link[Node]

object Main extends JSApp {
  def main() {
    val dimensions @ (width, height) = (400.0, 300.0)
    val svg = d3.select("#container")
      .append("svg")
      .attr("width", width)
      .attr("height", height)

    val nodes = js.Array(
      new Node(50, 50),
      new Node(60, 50),
      new Node(70, 50),
      new Node(100, 100)
    )
    val links = js.Array(
      new Link(nodes(0), nodes(1)),
      new Link(nodes(1), nodes(2)),
      new Link(nodes(3), nodes(2)),
      new Link(nodes(2), nodes(0)),
      new Link(nodes(3), nodes(1))
    )

    var force = d3.layout.force()
      .nodes(nodes)
      .links(links)
      .size(dimensions)
      .linkDistance(60)

    val synapses = svg.append("g").selectAll("line")
      .data(links)
    val neurons = svg.append("g").selectAll("circle")
      .data(nodes)
    val spikes = svg.append("g")

    synapses.enter()
      .append("line")
      .attr("stroke", "gray")

    neurons.enter()
      .append("circle")
      .attr("r", 10)
      .attr("fill", "#7DBBFF")
      .attr("stroke", "black")
      .attr("stroke-width", "1")

    force.on("tick", { event =>
      synapses
        .attr("x1", (d: Link) => d.source.x)
        .attr("y1", (d: Link) => d.source.y)
        .attr("x2", (d: Link) => d.target.x)
        .attr("y2", (d: Link) => d.target.y)

      neurons
        .attr("cx", (d: Node) => d.x)
        .attr("cy", (d: Node) => d.y)
    })

    def sendSpike(source: Node, target: Node) {
      spikes
        .append("circle")
        .attr("r", 5)
        .attr("fill", "#FFE77D")
        .attr("stroke", "black")
        .attr("stroke-width", "1")
        .attr("cx", source.x.get)
        .attr("cy", source.y.get)
        .transition().duration(300)
        .attr("cx", target.x.get)
        .attr("cy", target.y.get)
        .remove()

      setTimeout(300) {
        val outgoing = links.filter(l => l.source == target)
        val incoming = links.filter(l => l.target == target).map(l => new Link(l.target, l.source))
        val all = outgoing ++ incoming
        val link = all(util.Random.nextInt.abs % all.size)
        sendSpike(link.source, link.target)
      }
    }

    force.on("end", { event =>
      val link = links(util.Random.nextInt.abs % links.size)
      setTimeout(0) { sendSpike(link.source, link.target) }
      setTimeout(100) { sendSpike(link.source, link.target) }
      setTimeout(200) { sendSpike(link.source, link.target) }
    })

    force.start()
  }
}
