package telegram

import info.mukel.telegrambot4s.api.declarative.{Commands, InlineQueries}
import info.mukel.telegrambot4s.api.{Polling, TelegramBot}

object Main extends App {
  override def main(args: Array[String]): Unit = {
    Bot.run()
  }
}

object Bot extends TelegramBot with Polling with Commands with InlineQueries {

  lazy val token: String = scala.io.Source.fromFile("tg.token").getLines.toList.head.trim

  onCommand("/test") { implicit msg =>
    println(msg)
    reply("My token is SAFE!") }

  onMessage {
    implicit msg =>
      if (msg.location.isDefined) {
        println(msg)
        reply("Hello")

      }
  }
}