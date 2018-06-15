package telegram

import info.mukel.telegrambot4s.api.declarative.{Commands, InlineQueries}
import info.mukel.telegrambot4s.api.{Polling, TelegramBot}
import model.{Coor, Forecast, Shows}
import model.Forecast.{SimpleTimeLineForecase}
import web.{Holder, WebServer}

import scala.concurrent.Future

object Main extends App {
  override def main(args: Array[String]): Unit = {
    Bot.run()
  }
}

object Bot extends TelegramBot with Polling with Commands with InlineQueries {
  implicit val akkaSystem = Holder.system

  lazy val token: String = scala.io.Source.fromFile("tg.token").getLines.toList.head.trim

  onCommand("/test") { implicit msg =>
    println(msg)
    reply("My token is SAFE!")
  }

  onMessage {
    implicit msg =>
      if (msg.location.isDefined) {

        msg.location.foreach(location => {
          val coor = Coor(location.longitude, location.latitude)
          val f: Future[SimpleTimeLineForecase] = WebServer.getData(location.longitude, location.latitude)
          f.foreach(simpleTimeLineForecase => reply(Shows.showSimpleTimeLineForecase.show(simpleTimeLineForecase)))
        })

        logger.info(msg.toString)
      }
  }
}