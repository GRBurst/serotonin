package serotonin

import scala.scalajs.js.JSApp
import scala.scalajs.js
import scala.scalajs.js.timers._

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
      val circles = svg.selectAll("circle").data(list)

      circles.enter()
        .append("circle")
        .attr("r", 10)
        .attr("cx", (d: Int, i: Int) => d)
        .attr("cy", 100)

      circles
        .attr("cx", (d: Int, i: Int) => d)

      circles.exit()
        .remove()
    }

    setTimeout(2000) {
      update(js.Array(1))
    }
    setTimeout(3000) {
      update(js.Array(1, 20))
    }
    setTimeout(4000) {
      update(js.Array(1))
    }
    setTimeout(5000) {
      update(js.Array(30, 1))
    }
  }
}
