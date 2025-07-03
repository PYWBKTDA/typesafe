package user

import org.http4s._
import org.http4s.dsl.io._
import org.http4s.circe._
import io.circe.syntax._
import cats.effect._
import io.circe.Json
import user.Codecs._
import org.http4s.circe.CirceEntityCodec._
import org.http4s.server.Router
import org.typelevel.ci._
import io.circe.Encoder
import cats.syntax.all._

class UserRoutes(service: UserService) {

  val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {

    case req @ POST -> Root / "register" =>
      handleRequest(req.as[RegisterRequest])(service.register _)

    case req @ POST -> Root / "login" =>
      for {
        body   <- req.as[LoginRequest]
        result <- service.login(body)
        resp   <- result match {
          case Right(token) =>
            Ok(
              Json.obj(
                "status"  -> "success".asJson,
                "message" -> "Login successful".asJson,
                "data"    -> Json.obj("token" -> token.asJson)
              )
            )
          case Left(err) => errorResponse(err)
        }
      } yield resp

    case req @ POST -> Root / "update" =>
      extractToken(req) match {
        case Left(err)  => errorResponse(err)
        case Right(tok) => handleRequest(req.as[UpdateRequest])(service.update(tok, _))
      }

    case req @ GET -> Root / "uid" =>
      extractToken(req) match {
        case Left(err) => errorResponse(err)
        case Right(tok) =>
          service.getUidFromToken(tok).flatMap {
            case Right(id) =>
              Ok(
                Json.obj(
                  "status" -> "success".asJson,
                  "data"   -> Json.obj("uid" -> id.asJson)
                )
              )
            case Left(err) => errorResponse(err)
          }
      }

    case GET -> Root / "info" :? UidQueryParam(id) =>
      service.getUserInfo(id).flatMap(renderJsonField(_, "data"))
  }

  private def handleRequest[A](parse: IO[A])(logic: A => IO[Either[AppError, String]]): IO[Response[IO]] =
    parse.flatMap(d => logic(d).flatMap(renderMessage))

  private def renderMessage(r: Either[AppError, String]): IO[Response[IO]] =
    r.fold(errorResponse, msg => Ok(Json.obj("status" -> "success".asJson, "message" -> msg.asJson)))

  private def renderJsonField[A: Encoder](r: Either[AppError, A], key: String): IO[Response[IO]] =
    r.fold(errorResponse, v => Ok(Json.obj("status" -> "success".asJson, key -> v.asJson)))

  private def errorResponse(err: AppError): IO[Response[IO]] =
    Response[IO](statusFor(err)).withEntity(
      Json.obj("status" -> "error".asJson, "message" -> err.msg.asJson)
    ).pure[IO]

  private def statusFor(err: AppError): Status = err match {
    case _: AuthError       => Status.Unauthorized
    case _: NotFoundError   => Status.NotFound
    case _: ValidationError => Status.BadRequest
  }

  private def extractToken(req: Request[IO]): Either[AppError, String] =
    req.headers.get(ci"Authorization")
      .map(_.head.value.stripPrefix("Bearer ").trim)
      .filter(_.nonEmpty)
      .toRight(AuthError("No token"))

  object UidQueryParam extends QueryParamDecoderMatcher[String]("uid")

  def httpApp: HttpApp[IO] = Router("/user" -> routes).orNotFound
}
