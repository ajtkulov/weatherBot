package dao

import slick.jdbc.MySQLProfile.api._


object MysqlUtils {
  lazy val db = Database.forURL(
    url = "jdbc:mysql://127.0.0.1/weather?characterEncoding=UTF-8",
    user = "root",
    keepAliveConnection = true,
    password = "password"
  )
}
