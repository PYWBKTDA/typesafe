package course

import cats.effect._
import com.typesafe.config.ConfigFactory
import doobie.hikari.HikariTransactor
import org.http4s.ember.server.EmberServerBuilder
import com.comcast.ip4s._
import org.http4s.server.Router
import course._

object Main extends IOApp {

  override def run(args: List[String]): IO[ExitCode] = {
    val config = ConfigFactory.load()
    val dbConfig = config.getConfig("db")

    val transactorRes = HikariTransactor.newHikariTransactor[IO](
      dbConfig.getString("driver"),
      dbConfig.getString("url"),
      dbConfig.getString("user"),
      dbConfig.getString("password"),
      scala.concurrent.ExecutionContext.global
    )

    transactorRes.use { xa =>
      val repo = new CourseRepo(xa)
      val userClient = new UserClient
      val service = new CourseService(repo, userClient)
      val routes = new CourseRoutes(service)

      EmberServerBuilder
        .default[IO]
        .withHost(host"0.0.0.0")
        .withPort(port"8082")
        .withHttpApp(Router("/course" -> routes.routes).orNotFound)
        .build
        .useForever
        .as(ExitCode.Success)
    }
  }
}
