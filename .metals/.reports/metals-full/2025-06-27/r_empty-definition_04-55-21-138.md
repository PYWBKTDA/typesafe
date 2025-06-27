error id: file:///C:/Users/19567/Desktop/project/backend/build.sbt:
file:///C:/Users/19567/Desktop/project/backend/build.sbt
empty definition using pc, found symbol in pc: 
empty definition using semanticdb
empty definition using fallback
non-local guesses:

offset: 625
uri: file:///C:/Users/19567/Desktop/project/backend/build.sbt
text:
```scala
ThisBuild / scalaVersion := "3.4.2"

lazy val root = (project in file("."))
  .settings(
    name := "UserBackend",
    libraryDependencies ++= Seq(
      "org.typelevel"             %% "cats-effect"          % "3.5.4",
      "org.http4s"                %% "http4s-dsl"           % "0.23.25",
      "org.http4s"                %% "http4s-ember-server"  % "0.23.25",
      "org.http4s"                %% "http4s-circe"         % "0.23.25",
      "io.circe"                  %% "circe-generic"        % "0.14.7",
      "io.circe"                  %% "circe-parser"         % "0.14.7",
      "org.tpolecat"          @@    %% "doobie-core"          % "1.0.0-RC4",
      "org.tpolecat"              %% "doobie-hikari"        % "1.0.0-RC4",
      "org.tpolecat"              %% "doobie-postgres"      % "1.0.0-RC4",
      "org.flywaydb"               % "flyway-core"          % "10.13.0",
      "org.flywaydb"               % "flyway-database-postgresql" % "10.13.0",
      "org.postgresql"             % "postgresql"           % "42.7.3",
      "org.slf4j"                  % "slf4j-simple"         % "2.0.7"
    )
  )

```


#### Short summary: 

empty definition using pc, found symbol in pc: 