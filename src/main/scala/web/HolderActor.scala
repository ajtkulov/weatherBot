package web

import akka.actor.Actor
import model.{Coor, Forecast, SkyTimeLine}
import org.joda.time.Instant
import request.Request

sealed trait HolderActorMessages

case class Update() extends HolderActorMessages

case class Query(long: Double, lat: Double) extends HolderActorMessages

class HolderActor extends Actor {
  var state: Option[SkyTimeLine] = None

  override def receive: Receive = {
    case Update() => state = Some(Request.getModels(new Instant()))

    case Query(long, lat) => sender() ! state.map(model => model.forecast(Coor(lat, long))).getOrElse(Map())
  }
}
