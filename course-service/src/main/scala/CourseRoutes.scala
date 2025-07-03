package course

import cats.effect._
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.circe._
import io.circe._
import io.circe.syntax._
import io.circe.generic.auto._
import org.typelevel.ci.CIString

class CourseRoutes(service: CourseService) {

  val dsl = new Http4sDsl[IO] {}
  import dsl._

  implicit val createDecoder: EntityDecoder[IO, CreateCourseRequest] = jsonOf[IO, CreateCourseRequest]
  implicit val updateDecoder: EntityDecoder[IO, UpdateCourseRequest] = jsonOf[IO, UpdateCourseRequest]
  implicit val deleteDecoder: EntityDecoder[IO, DeleteCourseRequest] = jsonOf[IO, DeleteCourseRequest]
  implicit val enrollDecoder: EntityDecoder[IO, EnrollmentRequest] = jsonOf[IO, EnrollmentRequest]
  implicit val dropDecoder: EntityDecoder[IO, DropRequest] = jsonOf[IO, DropRequest]
  implicit val checkDecoder: EntityDecoder[IO, CheckRequest] = jsonOf[IO, CheckRequest]

  object IdQueryParamMatcher extends QueryParamDecoderMatcher[String]("id")
  object CourseIdMatcher extends QueryParamDecoderMatcher[String]("courseId")
  object UidMatcher extends QueryParamDecoderMatcher[String]("uid")

  val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {

    case req @ POST -> Root / "create" =>
      withAuth(req) { token =>
        for {
          body <- req.as[CreateCourseRequest]
          result <- service.createCourse(token, body)
          resp <- result.fold(
            err => StatusResponse.error(err),
            _ => Ok(ResponseWrapper.success("Course created"))
          )
        } yield resp
      }

    case req @ POST -> Root / "update" =>
      withAuth(req) { token =>
        for {
          body <- req.as[UpdateCourseRequest]
          result <- service.updateCourse(token, body)
          resp <- StatusResponse.fromBoolean(result, "Info updated")
        } yield resp
      }

    case req @ POST -> Root / "delete" =>
      withAuth(req) { token =>
        for {
          body <- req.as[DeleteCourseRequest]
          result <- service.deleteCourse(token, body)
          resp <- StatusResponse.fromBoolean(result, "Course deleted")
        } yield resp
      }

    case req @ POST -> Root / "select" =>
      withAuth(req) { token =>
        for {
          body <- req.as[EnrollmentRequest]
          result <- service.selectCourse(token, body)
          resp <- StatusResponse.fromBoolean(result, "Enrolled")
        } yield resp
      }

    case req @ POST -> Root / "drop" =>
      withAuth(req) { token =>
        for {
          body <- req.as[DropRequest]
          result <- service.dropCourse(token, body)
          resp <- StatusResponse.fromBoolean(result, "Dropped")
        } yield resp
      }

    case GET -> Root / "check" :? CourseIdMatcher(courseId) +& UidMatcher(uid) =>
      service.checkEnrollment(uid, courseId).flatMap {
        case Right(statusStr) =>
          Ok(ResponseWrapper.successWithData("Checked", Json.obj("status" -> Json.fromString(statusStr))))
        case Left(err) =>
          StatusResponse.responseFromError(err)
      }

    case GET -> Root / "list" =>
      service.listAllCourses.flatMap { courses =>
        Ok(ResponseWrapper.successWithData("Course list", courses.asJson))
      }

    case GET -> Root / "info" :? IdQueryParamMatcher(courseId) =>
      service.getCourseInfo(courseId).flatMap {
        case Right(info) => Ok(ResponseWrapper.successWithData("Course info", info.asJson))
        case Left(err)   => StatusResponse.responseFromError(err)
      }

    case req @ GET -> Root / "students" :? IdQueryParamMatcher(courseId) =>
      withAuth(req) { token =>
        service.listStudents(courseId, token).flatMap {
          case Right(list) => Ok(ResponseWrapper.successWithData("Student list", list.asJson))
          case Left(err)   => StatusResponse.error(err)
        }
      }
  }

  private def withAuth(req: Request[IO])(f: String => IO[Response[IO]]): IO[Response[IO]] =
    req.headers.get(CIString("Authorization")) match {
      case Some(h) => f(h.head.value)
      case None    => IO.pure(Response[IO](Status.Unauthorized).withEntity(ResponseWrapper.error("Missing Authorization header")))
    }

  object StatusResponse {
    def error(err: AppError): IO[Response[IO]] = {
      val status = err match {
        case ValidationError(_) => Status.BadRequest
        case AuthError(_)       => Status.Forbidden
        case NotFoundError(_)   => Status.NotFound
      }
      IO.pure(Response[IO](status).withEntity(ResponseWrapper.error(err.msg)))
    }

    def fromBoolean(result: Either[AppError, Boolean], successMsg: String): IO[Response[IO]] =
      result match {
        case Right(true)  => Ok(ResponseWrapper.success(successMsg))
        case Right(false) => NotFound(ResponseWrapper.error("Not found or not authorized"))
        case Left(err)    => error(err)
      }

    def responseFromError(err: AppError): IO[Response[IO]] = error(err)
  }

  case class SuccessResponse(status: String = "success", message: String, data: Option[Json] = None)
  case class ErrorResponse(status: String = "error", message: String)

  object ResponseWrapper {
    def success(message: String): Json =
      SuccessResponse(message = message).asJson

    def successWithData(message: String, data: Json): Json =
      SuccessResponse(message = message, data = Some(data)).asJson

    def error(message: String): Json =
      ErrorResponse(message = message).asJson
  }
}
