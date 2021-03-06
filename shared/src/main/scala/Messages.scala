package serotonin.actor
import akka.actor.ActorRef
import concurrent.duration._
import vectory._

object Messages {
  case class Strengthen(target: ActorRef)
  case object Fire
  case class SpikeEvent(neuron: ActorRef, time: FiniteDuration)
  implicit val mypotentialOrdering = Ordering.by[SpikeEvent, FiniteDuration](-_.time)
  case object ImDead
  case object IAmSourceOfYou
  sealed trait SensorType
  case class KeyboardSensorType(key: String) extends SensorType
  case class AddSensor(sensorType: SensorType, fixedPos: Option[Vec2] = None)
  case class AddMotor(motor: ActorRef, fixedPos: Option[Vec2] = None)
  case class AddNeuron(fireThreshold: Double)
  case class Signal(data: List[Double])

  case class AddedSynapse(source: ActorRef, target: ActorRef, weight: Double)
  case class AddedNeuron(neuron: ActorRef, fireThreshold: Double, fixedPos: Option[Vec2] = None)
  case class Spike(source: ActorRef, target: ActorRef, weight: Double)
  case class UpdatedFireThreshold(neuron: ActorRef, fireThreshold: Double)
  case class UpdatedSynapseWeight(source: ActorRef, target: ActorRef, weight: Double)
  case class UpdatedPotential(neuron: ActorRef, potential: Double)
}
