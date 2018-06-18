
name := "weatherBot "

version := "0.1"

lazy val akkaHttpVersion = "10.0.10"
lazy val akkaVersion    = "2.5.4"

scalaVersion := "2.12.3"

libraryDependencies += "org.scalactic" %% "scalactic" % "3.0.1"

libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.1" % "test"

libraryDependencies += "com.typesafe.play" %% "play-json" % "2.6.9"

libraryDependencies += "joda-time" % "joda-time" % "2.9.7"

parallelExecution in Test := false

libraryDependencies += "info.mukel" %% "telegrambot4s" % "3.0.14"

libraryDependencies += "org.slf4j" % "log4j-over-slf4j" % "1.7.16"

libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.1.1"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-http"            % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-stream"          % akkaVersion,
  "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
  "com.typesafe.akka" %% "akka-http-testkit"    % akkaHttpVersion % Test,
  "com.typesafe.akka" %% "akka-testkit"         % akkaVersion     % Test,
  "com.typesafe.akka" %% "akka-stream-testkit"  % akkaVersion     % Test,
  "org.scalatest"     %% "scalatest"            % "3.0.1"         % Test
)

fork := true

fork in run := true

libraryDependencies ++= Seq(
  "com.typesafe.slick" %% "slick" % "3.2.3",
  "mysql" % "mysql-connector-java" % "5.1.39",
  "org.joda" % "joda-convert" % "1.6",
  "com.github.tototoshi" %% "slick-joda-mapper" % "2.3.0",
  "com.typesafe.slick" %% "slick-codegen" % "3.2.3"
)

libraryDependencies += "com.typesafe" % "config" % "1.2.1"

enablePlugins(FlywayPlugin)

val weatherSqlUrl = settingKey[String]("weatherSqlUrl")

val weatherSqlUser = settingKey[String]("weatherSqlUser")

val weatherSqlPassword = settingKey[String]("weatherSqlPassword")

val dbPath = settingKey[String]("path to db migration scripts")

val historyFlywayTable = settingKey[String]("flywayTable")

weatherSqlUser := sys.props.getOrElse("user", default = "root")

weatherSqlPassword := sys.props.getOrElse("password", default = "password")

weatherSqlUrl := sys.props.getOrElse("url", default = "jdbc:mysql://localhost:3306/weather")

dbPath := sys.props.getOrElse("dbPath", default = "filesystem:db/sql")

historyFlywayTable := sys.props.getOrElse("historyFlywayTable", default = "schema_version")

flywayUser := weatherSqlUser.value

flywayPassword := weatherSqlPassword.value

flywayUrl := weatherSqlUrl.value

flywayLocations := Seq(dbPath.value)

flywayTable := historyFlywayTable.value
