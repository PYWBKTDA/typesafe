name := "user-service"

version := "0.1"

scalaVersion := "2.13.12"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-http" % "10.2.10",
  "com.typesafe.akka" %% "akka-stream" % "2.6.20",
  "com.typesafe.akka" %% "akka-actor-typed" % "2.6.20",
  "de.heikoseeberger" %% "akka-http-circe" % "1.39.2",
  "io.circe" %% "circe-core" % "0.14.1",
  "io.circe" %% "circe-generic" % "0.14.1",
  "io.circe" %% "circe-parser" % "0.14.1",
  "com.pauldijou" %% "jwt-core" % "5.0.0",
  "com.pauldijou" %% "jwt-circe" % "5.0.0",
  "com.github.t3hnar" %% "scala-bcrypt" % "4.3.0",
  "com.typesafe.slick" %% "slick" % "3.3.3",
  "org.postgresql" % "postgresql" % "42.3.1",
  "com.typesafe.slick" %% "slick-hikaricp" % "3.3.3",
  "com.typesafe.akka" %% "akka-http-testkit" % "10.2.10" % Test,
  "com.typesafe.akka" %% "akka-testkit" % "2.6.20" % Test,
  "org.scalatest" %% "scalatest" % "3.2.18" % Test
)
