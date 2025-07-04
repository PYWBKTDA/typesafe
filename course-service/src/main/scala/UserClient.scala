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
    val cleanToken =
  if (token.startsWith("Bearer ")) token
  else s"Bearer $token"

val req = Request[IO](Method.GET, uidUrl)
  .withHeaders(Headers(Header.Raw(ci"Authorization", cleanToken)))

    EmberClientBuilder.default[IO].build.use { client =>
      client.expect[String](req).attempt.flatMap {
        case Left(_) => IO.pure(None)
        case Right(body) =>
          parse(body).flatMap(_.hcursor.downField("data").get[String]("uid")) match {
            case Left(_) => IO.pure(None)
            case Right(uid) =>
              val infoReq = Request[IO](Method.GET, infoUrl.withQueryParam("uid", uid))
              client.expect[String](infoReq).attempt.map {
                case Left(_) => None
                case Right(infoBody) =>
                  val json  = parse(infoBody).getOrElse(Json.Null).hcursor.downField("data")
                  val role  = json.get[String]("type").getOrElse("")
                  val name  = json.get[String]("name").getOrElse("")
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
          parse(infoBody) match {
            case Left(_) => None
            case Right(json) =>
              val cursor = json.hcursor.downField("data")
              val role = cursor.get[String]("type").getOrElse("")
              val name = cursor.get[String]("name").getOrElse("")
              Some((role, name))
          }
      }
    }
  }
}
