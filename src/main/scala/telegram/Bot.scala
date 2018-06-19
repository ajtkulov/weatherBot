package telegram

import dao.{Location, Locations, MysqlUtils}
import info.mukel.telegrambot4s.api.declarative.{Commands, InlineQueries}
import info.mukel.telegrambot4s.api.{Extractors, Polling, TelegramBot}
import info.mukel.telegrambot4s.methods.{SendLocation, SendMessage}
import info.mukel.telegrambot4s.models.Message
import model.Shows
import org.joda.time.{DateTime, DateTimeZone, Instant}
import web.{Holder, WebServer}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try

object Main extends App {
  override def main(args: Array[String]): Unit = {
    Bot.run()
  }
}

object Bot extends TelegramBot with Polling with Commands with InlineQueries {
  implicit val akkaSystem = Holder.system

  akkaSystem.scheduler.schedule(1 minutes, 20 minutes, () => {
    val now = new DateTime(DateTimeZone.forID("Europe/Moscow"))
    val hour = now.getHourOfDay
    if (hour >= 5 && hour <= 22) {
      checkUsers()
    }
  })

  lazy val token: String = scala.io.Source.fromFile("tg.token").getLines.toList.head.trim

  onCommand("/help", "/?", "/start") { implicit msg =>
    val help =
      """
        | Прогноз осадков на 2 часа с шагом в 10 минут.
        |
        | Доступные команды:
        | /legend                      - обозначения
        | /help, /?                    - данная справка
        | отправить гео-точку          - просмотреть прогноз и добавить ее в список отслеживаемых
        | /checkAll                    - проверить прогноз по всем точкам
        | /showAll                     - показать данные по отмеченным гео-точкам
        | /show [номер точки]          - показать данные по конкретной гео-точке
        | /rename [номер точки] [имя]  - показать данные по конкретной гео-точке
        | /delete [номер точки]        - удалить гео-точку
      """.stripMargin
    reply(help)
  }

  onCommand("/checkAll") { implicit msg =>
    checkUser(msg.from.get.id)
  }

  onCommand("/legend") { implicit msg =>
    reply(
      """
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
      """.stripMargin)
  }

  onCommand("/delete") { implicit msg =>
    withArgs {
      case Seq(Extractors.Int(index)) if index > 0 =>
        for {
          _ <- MysqlUtils.db.run(Locations.deleteByUserIdAndIndex(msg.from.get.id, index))
          _ <- reply(s"Точка ${index} удалена")
        } yield ()
      case _ =>
        reply("/delete [номер точки], например /delete 1")
    }
  }

  onCommand("/showAll") { implicit msg =>
    for {
      active <- MysqlUtils.db.run(Locations.getByUserId(msg.from.get.id))
      _ = active.foreach {
        location => {
          for {
            _ <- reply(s"""${location.index}: ${location.name}""")
          } yield ()
        }
      }

    } yield ()
  }

  onCommand("/show") { implicit msg =>
    withArgs {
      case Seq(Extractors.Int(index)) if index > 0 =>
        for {
          locations <- MysqlUtils.db.run(Locations.getByUserIdAndIndex(msg.from.get.id, index))
          _ = locations.headOption.foreach(location => {
            for {
              _ <- reply(s"""${location.index}: ${location.name}""")
              _ <- request(SendLocation(location.chatId, location.latitude, location.longitude))
            } yield ()
          })
        } yield ()
      case _ =>
        reply("/show [номер точки], например /show 1")
    }
  }

  onCommand("/rename") { implicit msg =>
    withArgs { args => {
      if (args.size > 1 && Try {
        args.head.toInt
      }.isSuccess && args.head.toInt > 0) {
        val index = args.head.toInt
        for {
          locations <- MysqlUtils.db.run(Locations.getByUserIdAndIndex(msg.from.get.id, index))
          _ = locations.headOption.foreach(location => {
            MysqlUtils.db.run(Locations.insert(location.copy(name = args.mkString(" ").take(64))))
          })
          _ <- reply("Точка переименована")
        } yield ()
      }
      else {
        reply("/rename [номер точки] [название], например /rename 1 дом")
      }
    }
    }
  }

  onMessage {
    implicit msg =>
      msg.location.foreach(location => {
        for {
          byUser: Seq[Location] <- MysqlUtils.db.run(Locations.getByUserId(msg.from.get.id))
          indices: Set[Int] = byUser.map(x => x.index).toSet
          minIndex = ((1 to 5).toSet -- indices).minBy(identity)
          forecast <- WebServer.getData(location.longitude, location.latitude)
          _ <- reply(Shows.showSimpleTimeLineForecase.show(forecast))
          name = "some name"
          insert = Locations.insert(Location(None, msg.from.get.id, msg.chat.id, location.longitude, location.latitude, true, "", name, new Instant(), minIndex))
          _ <- MysqlUtils.db.run(insert)
          _ <- reply(s"Добавил точку $minIndex $name. Чтобы переименовать, воспользуйтесь /rename ${minIndex} [новое_имя]")
        } yield ()
        logger.info(msg.toString)
      })

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

  def checkUsers(): Future[Unit] = {
    for {
      active: Seq[Location] <- MysqlUtils.db.run(Locations.findActive())
      _ = active.foreach(location => {
        WebServer.getData(location.longitude, location.latitude).foreach(forecast => {
          if (forecast.values.toList.exists(x => x.inside.isDefined)) {
            val show = Shows.showSimpleTimeLineForecase.show(forecast)
            val result =
              s"""В точке ${location.index}: ${location.name} ожидаются осадки.
                 |$show
          """.stripMargin
            request(
              SendMessage(
                location.chatId,
                result
              )
            )
          }
        })
      })
    } yield ()
  }


}
