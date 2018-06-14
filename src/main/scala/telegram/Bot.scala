package telegram

import info.mukel.telegrambot4s.api.declarative.{Commands, InlineQueries}
import info.mukel.telegrambot4s.api.{Polling, TelegramBot}
import model.Forecast.TimeLineForecase
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
    reply("My token is SAFE!") }

  onMessage {
    implicit msg =>
      if (msg.location.isDefined) {

        msg.location.foreach(location => {
          val f: Future[TimeLineForecase] = WebServer.getData(location.longitude, location.latitude)
          f.map(x => reply(x.toString()))
        })

        println(msg)
        reply("Hello")
      }
  }
}