package web

import akka.actor.ActorSystem

object Holder {
  implicit val system = ActorSystem("my-system")
}
