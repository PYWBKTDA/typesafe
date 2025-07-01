ThisBuild / scalaVersion := "3.3.1"

libraryDependencies ++= Seq(
  "org.typelevel" %% "cats-effect"         % "3.5.4",
  "org.http4s"    %% "http4s-ember-server" % "0.23.25",
  "org.http4s"    %% "http4s-dsl"          % "0.23.25",
  "org.http4s"    %% "http4s-circe"        % "0.23.25",
  "io.circe"      %% "circe-generic"       % "0.14.7",
  "org.tpolecat"  %% "doobie-core"         % "1.0.0-RC4",
  "org.tpolecat"  %% "doobie-hikari"       % "1.0.0-RC4",
  "org.tpolecat"  %% "doobie-postgres"     % "1.0.0-RC4",
  "org.flywaydb"   % "flyway-core"                 % "10.22.0",
  "org.flywaydb"   % "flyway-database-postgresql"  % "10.22.0",
  "org.postgresql" % "postgresql"          % "42.7.3",
  "org.mindrot"    % "jbcrypt"             % "0.4",
  "org.slf4j"      % "slf4j-simple"        % "2.0.13"
)
