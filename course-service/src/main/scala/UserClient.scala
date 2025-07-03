package course

import cats.effect._
import org.http4s._
import org.http4s.client._
import org.http4s.client.dsl.io._
import org.http4s.ember.client._
import org.http4s.circe._
import io.circe._
import io.circe.parser._
import io.circe.generic.auto._
import org.http4s.implicits._
import org.typelevel.ci._

class UserClient {

  private val uidUrl = uri"http://localhost:8081/user/uid"
  private val infoUrl = uri"http://localhost:8081/user/info"

  def getUserInfo(token: String): IO[Option[(String, String, String)]] = {
    val req = Request[IO](Method.GET, uidUrl).withHeaders(Headers(Header.Raw(ci"Authorization", token)))

    EmberClientBuilder.default[IO].build.use { client =>
      client.expect[String](req).attempt.flatMap {
        case Left(_) => IO.pure(None)
        case Right(body) =>
          parse(body).flatMap(_.hcursor.get[String]("uid")) match {
            case Left(_) => IO.pure(None)
            case Right(uid) =>
              val infoReq = Request[IO](Method.GET, infoUrl.withQueryParam("uid", uid))
              client.expect[String](infoReq).attempt.map {
                case Left(_) => None
                case Right(infoBody) =>
                  val json = parse(infoBody).getOrElse(Json.Null)
                  val role = json.hcursor.get[String]("type").getOrElse("")
                  val name = json.hcursor.get[String]("name").getOrElse("")
                  Some((uid, role, name))
              }
          }
      }
    }
  }

  def getUserInfoByUid(uid: String): IO[Option[(String, String)]] = {
    val infoReq = Request[IO](Method.GET, infoUrl.withQueryParam("uid", uid))
    EmberClientBuilder.default[IO].build.use { client =>
      client.expect[String](infoReq).attempt.map {
        case Left(_) => None
        case Right(infoBody) =>
          val json = parse(infoBody).getOrElse(Json.Null)
          val role = json.hcursor.get[String]("type").getOrElse("")
          val name = json.hcursor.get[String]("name").getOrElse("")
          Some((role, name))
      }
    }
  }
}
