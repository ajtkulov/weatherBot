
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

//libraryDependencies += "com.typesafe.akka" %% "akka-http"   % "10.1.2"
//
//libraryDependencies += "com.typesafe.akka" %% "akka-stream" % "2.5.13"

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