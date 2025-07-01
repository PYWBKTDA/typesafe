package course

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.{ExceptionHandler, Route}
import akka.http.scaladsl.server.Directives._
import akka.stream.Materializer
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.parser._
import pdi.jwt.{JwtAlgorithm, JwtCirce}
import slick.jdbc.PostgresProfile.api._
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import scala.util.{Failure, Success}

case class Course(id: String, name: String, teacherId: String)
case class Enrollment(studentId: String, courseId: String)

case class CreateCourseRequest(name: String)
case class DeleteCourseRequest(courseId: String)
case class EnrollmentRequest(courseId: String)
case class DropRequest(courseId: String)

class CourseTable(tag: Tag) extends Table[Course](tag, "courses") {
  def id        = column[String]("id", O.PrimaryKey)
  def name      = column[String]("name")
  def teacherId = column[String]("teacher_id")
  def * = (id, name, teacherId) <> (Course.tupled, Course.unapply)
}

class EnrollmentTable(tag: Tag) extends Table[Enrollment](tag, "enrollments") {
  def studentId = column[String]("student_id")
  def courseId  = column[String]("course_id")
  def *         = (studentId, courseId) <> (Enrollment.tupled, Enrollment.unapply)
  def pk        = primaryKey("pk_enrollment", (studentId, courseId))
  def courseFk  = foreignKey("fk_course", courseId, Main.courses)(_.id, onDelete = ForeignKeyAction.Cascade)
}

object Main {
  implicit val system: ActorSystem          = ActorSystem("course-system")
  implicit val materializer: Materializer   = Materializer(system)
  implicit val ec: ExecutionContextExecutor = system.dispatcher

  val db          = Database.forConfig("db")
  val courses     = TableQuery[CourseTable]
  val enrollments = TableQuery[EnrollmentTable]

  private val jwtKey      = "secret"
  private val userInfoUrl = "http://localhost:8081/user/info"

  def verifyToken(token: String): Option[String] =
    JwtCirce.decode(token.stripPrefix("Bearer ").trim, jwtKey, Seq(JwtAlgorithm.HS256)).toOption.flatMap(_.subject)

  def validateRole(uid: String, role: String, token: String): Future[Boolean] = {
    val uri         = Uri(userInfoUrl).withQuery(Uri.Query("uid" -> uid))
    val httpRequest = HttpRequest(uri = uri).withHeaders(headers.RawHeader("Authorization", token))
    Http().singleRequest(httpRequest).flatMap { response =>
      if (response.status == StatusCodes.OK) {
        response.entity.toStrict(3.seconds).map { entity =>
          val jsonStr = entity.data.utf8String
          val roleMatch = parse(jsonStr).flatMap(_.hcursor.downField("type").as[String]) match {
            case Right(tpe) => tpe == role.toLowerCase
            case _          => false
          }
          roleMatch
        }
      } else Future.successful(false)
    }
  }

  implicit val exceptionHandler: ExceptionHandler =
    ExceptionHandler { case ex => complete(StatusCodes.InternalServerError -> ex.getMessage) }

  val routes: Route = handleExceptions(exceptionHandler) {
    pathPrefix("course") {
      path("list") {
        get {
          parameter("name".?) { nameOpt =>
            val query = nameOpt match {
              case Some(keyword) =>
                courses.filter(_.name.toLowerCase like s"%${keyword.toLowerCase}%")
              case None =>
                courses
            }
            onComplete(db.run(query.result)) {
              case Success(result) => complete(result.asJson)
              case Failure(ex)     => throw ex
            }
          }
        }
      } ~
      headerValueByName("Authorization") { auth =>
        val uidOpt = verifyToken(auth)
        concat(
          path("create") {
            post {
              entity(as[CreateCourseRequest]) { req =>
                uidOpt match {
                  case Some(uid) =>
                    onComplete(validateRole(uid, "teacher", auth)) {
                      case Success(true) =>
                        val checkExist = courses.filter(c => c.name === req.name && c.teacherId === uid).exists.result
                        onComplete(db.run(checkExist)) {
                          case Success(true)  => complete(StatusCodes.Conflict -> "You have already created this course")
                          case Success(false) =>
                            val id = java.util.UUID.randomUUID().toString
                            onComplete(db.run(courses += Course(id, req.name, uid))) {
                              case Success(_)  => complete(StatusCodes.OK -> Map("courseId" -> id))
                              case Failure(ex) => throw ex
                            }
                          case Failure(ex) => throw ex
                        }
                      case _ => complete(StatusCodes.Unauthorized)
                    }
                  case None => complete(StatusCodes.Unauthorized)
                }
              }
            }
          },
          path("select") {
            post {
              entity(as[EnrollmentRequest]) { req =>
                uidOpt match {
                  case Some(uid) =>
                    onComplete(validateRole(uid, "student", auth)) {
                      case Success(true) =>
                        val checkSelected    = enrollments.filter(e => e.studentId === uid && e.courseId === req.courseId).exists.result
                        val checkCourseExist = courses.filter(_.id === req.courseId).exists.result
                        val action =
                          checkCourseExist.flatMap { courseExists =>
                            if (!courseExists)
                              DBIO.failed(new Exception("Course not found"))
                            else {
                              checkSelected.flatMap { alreadySelected =>
                                if (alreadySelected)
                                  DBIO.failed(new Exception("Already selected"))
                                else
                                  enrollments += Enrollment(uid, req.courseId)
                              }
                            }
                          }
                        onComplete(db.run(action.transactionally)) {
                          case Success(_) => complete("Selected")
                          case Failure(e) =>
                            if (e.getMessage == "Course not found")
                              complete(StatusCodes.BadRequest -> "Course does not exist")
                            else if (e.getMessage == "Already selected")
                              complete(StatusCodes.Conflict -> "Already selected")
                            else throw e
                        }
                      case _ => complete(StatusCodes.Unauthorized)
                    }
                  case None => complete(StatusCodes.Unauthorized)
                }
              }
            }
          },
          path("drop") {
            post {
              entity(as[DropRequest]) { req =>
                uidOpt match {
                  case Some(uid) =>
                    onComplete(validateRole(uid, "student", auth)) {
                      case Success(true) =>
                        onComplete(db.run(enrollments.filter(e =>
                          e.studentId === uid && e.courseId === req.courseId).delete)) {
                          case Success(0) => complete(StatusCodes.BadRequest -> "No enrollment record")
                          case Success(_) => complete("Dropped")
                          case Failure(e) => throw e
                        }
                      case _ => complete(StatusCodes.Unauthorized)
                    }
                  case None => complete(StatusCodes.Unauthorized)
                }
              }
            }
          },
          path("delete") {
            post {
              entity(as[DeleteCourseRequest]) { req =>
                uidOpt match {
                  case Some(uid) =>
                    onComplete(validateRole(uid, "teacher", auth)) {
                      case Success(true) =>
                        onComplete(db.run(courses.filter(c =>
                          c.id === req.courseId && c.teacherId === uid).delete)) {
                          case Success(0) => complete(StatusCodes.Unauthorized)
                          case Success(_) => complete("Deleted")
                          case Failure(e) => throw e
                        }
                      case _ => complete(StatusCodes.Unauthorized)
                    }
                  case None => complete(StatusCodes.Unauthorized)
                }
              }
            }
          }
        )
      }
    }
  }
}

object Boot extends App {
  import Main._
  Http().newServerAt("localhost", 8082).bind(routes)
  Await.ready(Future.never, Duration.Inf)
}
