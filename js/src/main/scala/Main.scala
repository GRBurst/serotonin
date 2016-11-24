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

    val nodes = js.Array(new Node(), new Node())
    val links = js.Array(new Link(nodes(0), nodes(1)))

    var force = d3.layout.force()
      .nodes(nodes)
      .links(links)
      .size(dimensions)
      .linkDistance(60)

    val circles = svg.selectAll("circle")
      .data(nodes)
    val lines = svg.selectAll("line")
      .data(links)

    lines.enter()
      .append("line")
      .attr("stroke", "gray")

    circles.enter()
      .append("circle")
      .attr("r", 10)
      .attr("fill", "green")

    force.on("tick", { event =>
      lines
        .attr("x1", (d: Link) => d.source.x)
        .attr("y1", (d: Link) => d.source.y)
        .attr("x2", (d: Link) => d.target.x)
        .attr("y2", (d: Link) => d.target.y)

      circles
        .attr("cx", (d: Node) => d.x)
        .attr("cy", (d: Node) => d.y)
    })

    force.start()
  }
}
