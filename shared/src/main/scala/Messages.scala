package serotonin
import akka.actor.ActorRef
import concurrent.duration._

object Messages {
  case class Strengthen(target: ActorRef)
  case object Fire
  case object Hebb // what fires together wires together
  case object HebbEvaluation
  case object Probe
  case class FireEvent(neuron: ActorRef, time: FiniteDuration)
  implicit val mypotentialOrdering = Ordering.by[FireEvent, FiniteDuration](-_.time)
  case object ImDead
  case object IFollowYou
  case object AddSensor
  case object AddAction
  case class AddNeuron(fireThreshold: Double)
  case class Signal(data: List[Double])
  case object Graph
  case object DumpGraph
}
