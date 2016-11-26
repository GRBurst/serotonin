package serotonin.actor

import akka.actor._
import Messages._
import org.scalajs.dom._

class KeyboardSensor(key: String, target: ActorRef) extends Actor {
  document.addEventListener("keydown", (event: KeyboardEvent) => {
    if (event.key == key)
      target ! Fire
  })

  def receive: Receive = {
    case _ =>
  }
}
