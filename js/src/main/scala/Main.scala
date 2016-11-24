package serotonin

import scala.scalajs.js.JSApp
import scala.scalajs.js
import scala.scalajs.js.timers._
import js.JSConverters._

import org.singlespaced.d3js.Ops._
import org.singlespaced.d3js.d3

object Main extends JSApp {
  def main() {
    val svg = d3.select("#container")
      .append("svg")
      .attr("width", 300)
      .attr("height", 400)

    def update(list: js.Array[Int]) {
      println(list)
      val circles = svg.selectAll("circle").data(list, (d:Int) => d.toString)

      circles.enter()
        .append("circle")
        .attr("r", 0)
        .attr("cx", (d: Int) => d)
        .attr("cy", 50)
        // .style("opacity", 0)
        .attr("fill", "green")

      circles.transition().duration(1000)
        .attr("r", 10)
        .attr("cx", (d: Int) => d)
        .attr("cy", 100)
        // .style("opacity", 1)
        .attr("fill", "black")

      circles.exit().transition().duration(1000)
        .attr("r", 0)
        // .style("opacity", 0)
        .attr("cy", 150)
        .attr("fill", "red")
        .remove()
    }

    setInterval(2000) {
      update(Seq.fill(5)(20 + (util.Random.nextInt.abs % 5)*30 ).toJSArray)
    }
  }
}
