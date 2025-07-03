ThisBuild / scalaVersion := "2.13.14"

val http4sVersion = "0.23.23"
val doobieVersion = "1.0.0-RC4"
val circeVersion = "0.14.6"

libraryDependencies ++= Seq(
  "org.typelevel" %% "cats-effect" % "3.5.2",
  "org.http4s" %% "http4s-dsl" % http4sVersion,
  "org.http4s" %% "http4s-ember-server" % http4sVersion,
  "org.http4s" %% "http4s-ember-client" % http4sVersion,
  "org.http4s" %% "http4s-circe" % http4sVersion,
  "io.circe" %% "circe-core" % circeVersion,
  "io.circe" %% "circe-generic" % circeVersion,
  "io.circe" %% "circe-parser" % circeVersion,
  "org.tpolecat" %% "doobie-core" % doobieVersion,
  "org.tpolecat" %% "doobie-hikari" % doobieVersion,
  "org.tpolecat" %% "doobie-postgres" % doobieVersion,
  "org.postgresql" % "postgresql" % "42.6.0",
  "com.github.t3hnar" %% "scala-bcrypt" % "4.3.0",
  "com.github.jwt-scala" %% "jwt-circe" % "9.4.5",
  "com.typesafe" % "config" % "1.4.3",
  "org.typelevel" %% "munit-cats-effect" % "2.0.0-M3" % Test
)
