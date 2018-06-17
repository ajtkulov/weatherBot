package telegram

import dao.{Location, Locations, MysqlUtils}
import info.mukel.telegrambot4s.api.declarative.{Commands, InlineQueries}
import info.mukel.telegrambot4s.api.{Polling, TelegramBot}
import info.mukel.telegrambot4s.methods.{SendLocation, SendMessage}
import info.mukel.telegrambot4s.models.{ChatId, Message}
import model.{Coor, Forecast, Shows}
import model.Forecast.SimpleTimeLineForecast
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
        | Прогноз осадков на 2 часа с шагом в 10 минут.
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
        |
        | Доступные команды:
        | /help, /? - данная справка
        | отправить гео-точку - просмотреть прогноз и добавить ее в список отслеживаемых
        | /checkAll - проверить прогноз по всем точкам
        | /showAll  - показать данные по отмеченным гео-точкам
      """.stripMargin
    reply(help)
  }

  onCommand("/checkAll") { implicit msg =>
    checkUser(msg.from.get.id)
  }

  onCommand("/showAll") { implicit msg =>
    for {
      active <- MysqlUtils.db.run(Locations.getByUserId(msg.from.get.id))
      _ = active.foreach {
        location => {
          for {
            _ <- reply(s"""${location.index}: ${location.name}""")
            _ <- request (SendLocation(location.chatId, location.latitude, location.longitude))
          } yield ()
        }
      }

    } yield ()
  }

  onMessage {
    implicit msg =>
      if (msg.location.isDefined) {

        msg.location.foreach(location => {
          for {
            byUser: Seq[Location] <- MysqlUtils.db.run(Locations.getByUserId(msg.from.get.id))
            indecies: Set[Int] = byUser.map(x => x.index).toSet
            minIndex = ((1 to 5).toSet -- indecies).minBy(identity)
            forecast <- WebServer.getData(location.longitude, location.latitude)
            _ <- reply(Shows.showSimpleTimeLineForecase.show(forecast))
            insert = Locations.insert(Location(None, msg.from.get.id, msg.chat.id, location.longitude, location.latitude, true, "", "some name", new Instant(), minIndex))
            _ <- MysqlUtils.db.run(insert)
          } yield ()
        })

        logger.info(msg.toString)
      }
  }

  def checkUser(userId: Int)(implicit msg: Message): Future[Unit] = {
    for {
      active: Seq[Location] <- MysqlUtils.db.run(Locations.getByUserId(userId))
      _ = active.foreach(location => {
        WebServer.getData(location.longitude, location.latitude).foreach(forecast => {
          val show = Shows.showSimpleTimeLineForecase.show(forecast)
          val result =
            s"""${location.index}: ${location.name}
               |$show
          """.stripMargin
          reply(result)
        })
      })
    } yield ()
  }
}
