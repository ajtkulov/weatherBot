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
import cats.data.OptionT
import cats.implicits._
import telegram.Bot.reply

object Main extends App {
  override def main(args: Array[String]): Unit = {
    WebServer.init()
    Bot.run()
  }
}

object Bot extends TelegramBot with Polling with Commands with InlineQueries {
  implicit val akkaSystem = Holder.system

  akkaSystem.scheduler.schedule(20 minutes, 20 minutes, () => {
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
        | [номер точки]                - тоже что и /show [номер точки]
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
        | означает, что первый час в точке идет дождь (первые 6 иконок), затем отсутствие осадков через час. Каждая иконка показывает прогноз на 10 минут.
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
      _ <- Future.traverse(active)(location => reply(s"""${location.index}: ${location.name}"""))
    } yield ()
  }

  def show(index: Int)(implicit msg: Message): OptionT[Future, Unit] = {
    for {
      userId <- OptionT.fromOption[Future](msg.from)
      locations: Seq[Location] <- OptionT.liftF(MysqlUtils.db.run(Locations.getByUserIdAndIndex(userId.id, index)))
      _ <- locations.headOption match {
        case None => OptionT.liftF(reply(s"Не существующая точка ${index}"))
        case Some(location) => OptionT.liftF(show(location))
      }
    } yield ()
  }

  def show(location: Location)(implicit msg: Message): Future[Unit] = {
    for {
      _ <- reply(s"""${location.index}: ${location.name}""")
      _ <- request(SendLocation(location.chatId, location.latitude, location.longitude))
      forecast <- WebServer.getData(location.longitude, location.latitude)
      _ <- reply(Shows.showSimpleTimeLineForecase.show(forecast))
    } yield ()
  }

  onCommand("/show") {
    implicit msg =>
      withArgs {
        case Seq(Extractors.Int(index)) if index > 0 =>
          show(index)
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
          locations <- OptionT.liftF(MysqlUtils.db.run(Locations.getByUserIdAndIndex(msg.from.get.id, index)))
          location <- OptionT.fromOption[Future](locations.headOption)
          _ <- OptionT.liftF(MysqlUtils.db.run(Locations.insert(location.copy(name = args.drop(1).mkString(" ").take(64)))))
          _ <- OptionT.liftF(reply("Точка переименована"))
        } yield ()
      }
      else {
        reply("/rename [номер точки] [название], например /rename 1 дом")
      }
    }
    }
  }

  def getIdx(msg: Message): Option[Int] = {
    msg.text.map(x => {
      val i: Int = Try {
        x.toInt
      }.getOrElse(-1)
      i
    })
  }

  onMessage {
    implicit msg => {
      if (msg.location.isDefined) {
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
        })
      } else if (getIdx(msg).getOrElse(-1) >= 1) {
        show(getIdx(msg).get)
      }

      logger.info(msg.toString)
    }
  }

  def checkUser(userId: Int)(implicit msg: Message): Future[Unit] = {
    for {
      active: Seq[Location] <- MysqlUtils.db.run(Locations.getByUserId(userId))
      forecasts <- Future.traverse(active)(location => WebServer.getData(location.longitude, location.latitude).map(x => (x, location)))
      shows = forecasts.map(x => (Shows.showSimpleTimeLineForecase.show(x._1), x._2))
      results = shows.map { case (show, location) =>
        s"""${location.index}: ${location.name}
           | $show
          """.stripMargin
      }
      _ <- Future.traverse(results)(x => reply(x))
    } yield ()
  }

  def checkUsers(): Future[Unit] = {
    for {
      active: Seq[Location] <- MysqlUtils.db.run(Locations.findActive())
      _ <- Future.traverse(active)(location => {
        WebServer.getData(location.longitude, location.latitude).flatMap(forecast => {
          if (forecast.values.toList.exists(x => x.inside.isDefined)) {
            val show = Shows.showSimpleTimeLineForecase.show(forecast)
            val result =
              s"""В точке ${location.index}: ${location.name} ожидаются осадки.
                 |$show
               """.stripMargin
            request(SendMessage(location.chatId, result))
          } else {
            Future.successful()
          }
        })
      })
    } yield ()
  }
}
