package dao

import slick.lifted.Tag
import slick.jdbc.MySQLProfile.api._

case class Location(id: Option[Int], userId: Int, chatId: Long, longitude: Double, latitue: Double, enable: Boolean, schedule: String, name: String) {}

class Locations(tag: Tag) extends Table[Location](tag, "location") {
  def id = column[Int]("id", O.PrimaryKey, O.AutoInc)

  def userId = column[Int]("user_id")
  def chatId = column[Long]("chat_id")
  def longitute = column[Double]("longitude")
  def latitude = column[Double]("latitude")
  def enable = column[Boolean]("enable")
  def schedule = column[String]("schedule")
  def name = column[String]("NAME")

  def * = (id.?, userId, chatId, longitute, latitude, enable, schedule, name) <> (Location.tupled, Location.unapply)
}

object Locations {
  val table = TableQuery[Locations]

  def findActive() = {
    table.filter(location => location.enable).result
  }

  def insert(value: Location) = {
    table.insertOrUpdate(value)
  }
}