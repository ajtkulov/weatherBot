package web

import akka.actor.Props
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import akka.http.scaladsl.server.Directives._
import akka.pattern._
import akka.util.Timeout
import model.Forecast.{SimpleTimeLineForecast, TimeLineForecase}
import model.{Coor, Forecast, Serialization, SimpleForecast}
import org.joda.time.Instant
import org.slf4j.LoggerFactory

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util._

/**
  * http://localhost:8080/get?long=56.846230&lat=53.229994
  */
object WebServer {
  lazy val logger = LoggerFactory.getLogger(getClass)
  implicit val system = Holder.system
  implicit val materializer = ActorMaterializer()
  implicit val timeout: akka.util.Timeout = Timeout(10 seconds)
  lazy val holderActor = system.actorOf(Props(new HolderActor()))
  implicit val executionContext = system.dispatcher

  holderActor ! Update()

  system.scheduler.schedule(0 minutes, 10 minutes, holderActor, Update())(executionContext)

  def getData(long: BigDecimal, lat: BigDecimal): Future[SimpleTimeLineForecast] = (holderActor ? Query(long, lat)).map(any => any.asInstanceOf[Forecast.TimeLineForecase].mapValues(_.toSimple(Coor(long, lat))))

  def main(args: Array[String]) {

    val route =
      path("get") {
        get {
          parameters('long.as[Double], 'lat.as[Double]) { (long, lat) =>
            onComplete(getData(long, lat)) {
              case Success(value) => complete(HttpEntity(ContentTypes.`application/json`, Serialization.writerComplex.writes(value).toString()))
              case Failure(value) => complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, value.toString()))
            }
          }
        }
      }

    logger.info("Starting web server on port 8080")
    Http().bindAndHandle(route, "localhost", 8080)
  }

  def init(): Unit = {
    holderActor ! Update()
  }
}
