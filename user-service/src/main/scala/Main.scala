package user

import cats.effect._
import com.typesafe.config.ConfigFactory
import doobie.hikari.HikariTransactor
import org.http4s.ember.server.EmberServerBuilder
import com.comcast.ip4s._
import scala.concurrent.ExecutionContext

object Main extends IOApp {

  override def run(args: List[String]): IO[ExitCode] = {
    val config = ConfigFactory.load()
    val db = config.getConfig("db")

    val transactorRes = HikariTransactor.newHikariTransactor[IO](
      db.getString("driver"),
      db.getString("url"),
      db.getString("user"),
      db.getString("password"),
      ExecutionContext.global
    )

    transactorRes.use { xa =>
      val repo = new UserRepo(xa)
      val tokenService = new JwtUtil
      val service = new UserService(repo, tokenService)
      val routes = new UserRoutes(service)

      EmberServerBuilder
        .default[IO]
        .withHost(host"0.0.0.0")
        .withPort(port"8081")
        .withHttpApp(routes.httpApp)
        .build
        .useForever
        .as(ExitCode.Success)
    }
  }
}
