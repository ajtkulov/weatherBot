package web

import akka.actor.Actor
import com.github.nscala_time.time.Imports._
import model.{Coor, SkyTimeLine}
import org.joda.time.DateTime
import request.Request

sealed trait HolderActorMessages

case class Update() extends HolderActorMessages

case class Query(long: BigDecimal, lat: BigDecimal) extends HolderActorMessages

class HolderActor extends Actor {
  var state: Option[SkyTimeLine] = None
  var timestamp: DateTime = new DateTime(0)

  override def receive: Receive = {
    case Update() => {
      if (new DateTime() >= timestamp.plusMinutes(5)) {
        state = Some(Request.getModels())
        timestamp = new DateTime()
      }
    }

    case Query(long, lat) => sender() ! state.map(model => model.forecast(Coor(long, lat))).getOrElse(Map())
  }
}
