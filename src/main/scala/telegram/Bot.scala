package telegram

import dao.{Location, Locations, MysqlUtils}
import info.mukel.telegrambot4s.api.declarative.{Commands, InlineQueries}
import info.mukel.telegrambot4s.api.{Polling, TelegramBot}
import info.mukel.telegrambot4s.methods.SendMessage
import info.mukel.telegrambot4s.models.ChatId
import model.{Coor, Forecast, Shows}
import model.Forecast.SimpleTimeLineForecase
import org.joda.time.Instant
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

  onCommand("/help", "/?") { implicit msg =>
    val help =
      """
        | Прогноз осадков на 2 часа с шагов в 10 минут.
        |
        | Обозначения:
        | 🌦️ - слабый дождь
        | 🌧 - дождь
        | ⛈ - сильный дождь
        | ⚡ - сильнее некуда
        | ☁ - очень близко (<2 км) идет дождь
        | ❔- на расстоянии 2-5 км идет дождь
        | ⛅ - на расстоянии 5-10 км идет дождь
        | 🌤 - на расстоянии 10-50 км идет дождь
        | ☀ - на расстоянии 50 км нигде нет осадков
        | ❄ - снег
        |
        | Например,
        | 🌦️ 🌦️ 🌧 🌧 🌦️ 🌦️ ☁️ ❔ 🌤 🌤 🌤 ☀ ☀ ☀
        | означает, что первый час в точке идет дождь (первые 6 иконок), затем отсутствие осадков через час.
      """.stripMargin
    reply(help)
  }

  onMessage {
    implicit msg =>
      if (msg.location.isDefined) {

        msg.location.foreach(location => {
          val coor = Coor(location.longitude, location.latitude)
          val f: Future[SimpleTimeLineForecase] = WebServer.getData(location.longitude, location.latitude)
          f.foreach(simpleTimeLineForecase => reply(Shows.showSimpleTimeLineForecase.show(simpleTimeLineForecase)))

          for {
            byUser: Seq[Location] <- MysqlUtils.db.run(Locations.getByUserId(msg.from.get.id))
            indecies: Set[Int] = byUser.map(x => x.index).toSet
            minIndex = ((1 to 5).toSet -- indecies).minBy(identity)
            insert = Locations.insert(Location(None, msg.from.get.id, msg.chat.id, location.longitude, location.latitude, true, "", "some name", new Instant(), minIndex))
            _ <- MysqlUtils.db.run(insert)

          } yield ()
        })

        logger.info(msg.toString)
      }
  }
}