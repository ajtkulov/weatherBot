package dao

import org.joda.time.Instant
import slick.lifted.Tag
import slick.jdbc.MySQLProfile.api._
import com.github.tototoshi.slick.MySQLJodaSupport._

case class Location(id: Option[Int], userId: Int, chatId: Long, longitude: Double, latitude: Double, enable: Boolean, schedule: String, name: String, lastCheck: Instant, index: Int) {}

class Locations(tag: Tag) extends Table[Location](tag, "location") {
  def id = column[Int]("id", O.PrimaryKey, O.AutoInc)

  def userId = column[Int]("user_id")
  def chatId = column[Long]("chat_id")
  def longitute = column[Double]("longitude")
  def latitude = column[Double]("latitude")
  def enable = column[Boolean]("enable")
  def schedule = column[String]("schedule")
  def name = column[String]("name")
  def lastCheck = column[Instant]("last_check")
  def index = column[Int]("index")

  def * = (id.?, userId, chatId, longitute, latitude, enable, schedule, name, lastCheck, index) <> (Location.tupled, Location.unapply)
}

object Locations {
  val table = TableQuery[Locations]

  def findActive() = {
    table.filter(location => location.enable).result
  }

  def insert(value: Location) = {
    table.insertOrUpdate(value)
  }

  def getByUserId(userId: Int) = {
    table.filter(location => location.userId === userId).result
  }
}