package course

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.{ExceptionHandler, Route}
import akka.http.scaladsl.server.Directives._
import akka.stream.Materializer
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.parser._
import io.circe.generic.auto._
import io.circe.syntax._
import slick.jdbc.PostgresProfile.api._
import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success}

case class Course(id: String, name: String, uid: String, teacherName: String, time: String, location: String)
case class Enrollment(uid: String, courseId: String)

case class CreateCourseRequest(name: String, time: String, location: String)
case class UpdateCourseRequest(courseId: String, name: String, time: String, location: String)
case class DeleteCourseRequest(courseId: String)
case class EnrollmentRequest(courseId: String)
case class DropRequest(courseId: String)
case class CheckRequest(courseId: String, uid: String)

class CourseTable(tag: Tag) extends Table[Course](tag, "courses") {
  def id = column[String]("id", O.PrimaryKey)
  def name = column[String]("name")
  def uid = column[String]("uid")
  def teacherName = column[String]("teacher_name")
  def time = column[String]("time")
  def location = column[String]("location")
  def * = (id, name, uid, teacherName, time, location) <> (Course.tupled, Course.unapply)
}

class EnrollmentTable(tag: Tag) extends Table[Enrollment](tag, "enrollments") {
  def uid = column[String]("uid")
  def courseId = column[String]("course_id")
  def * = (uid, courseId) <> (Enrollment.tupled, Enrollment.unapply)
  def pk = primaryKey("pk_enrollment", (uid, courseId))
  def courseFk = foreignKey("fk_course", courseId, Main.courses)(_.id, onDelete = ForeignKeyAction.Cascade)
}

object Main {
  implicit val system: ActorSystem = ActorSystem("course-system")
  implicit val materializer: Materializer = Materializer(system)
  implicit val ec: ExecutionContextExecutor = system.dispatcher

  val db = Database.forConfig("db")
  val courses = TableQuery[CourseTable]
  val enrollments = TableQuery[EnrollmentTable]

  private val userUidUrl = "http://localhost:8081/user/uid"
  private val userInfoUrl = "http://localhost:8081/user/info"

  def extractUidAndRole(token: String): Future[Option[(String, String, String)]] = {
    val req = HttpRequest(uri = userUidUrl).withHeaders(headers.RawHeader("Authorization", token))
    Http().singleRequest(req).flatMap { res =>
      if (res.status != StatusCodes.OK) Future.successful(None)
      else res.entity.toStrict(3.seconds).flatMap { entity =>
        val uidOpt = parse(entity.data.utf8String).flatMap(_.hcursor.get[String]("uid")).toOption
        uidOpt match {
          case Some(uid) =>
            val infoReq = HttpRequest(uri = Uri(userInfoUrl).withQuery(Uri.Query("uid" -> uid)))
            Http().singleRequest(infoReq).flatMap { infoRes =>
              if (infoRes.status != StatusCodes.OK) Future.successful(None)
              else infoRes.entity.toStrict(3.seconds).map { ent =>
                val json = parse(ent.data.utf8String).getOrElse(io.circe.Json.Null)
                val role = json.hcursor.get[String]("type").getOrElse("")
                val name = json.hcursor.get[String]("name").getOrElse("")
                Some((uid, role, name))
              }
            }
          case None => Future.successful(None)
        }
      }
    }
  }

  implicit val exceptionHandler: ExceptionHandler = ExceptionHandler {
    case ex => complete(StatusCodes.InternalServerError -> ex.getMessage)
  }

  val routes: Route = handleExceptions(exceptionHandler) {
    pathPrefix("course") {
      headerValueByName("Authorization") { auth =>
        concat(
          path("create") {
            post {
              entity(as[CreateCourseRequest]) { req =>
                onComplete(extractUidAndRole(auth)) {
                  case Success(Some((uid, "teacher", name))) =>
                    val exists = courses.filter(c => c.uid === uid && c.name === req.name).exists.result
                    onComplete(db.run(exists)) {
                      case Success(true) => complete(StatusCodes.Conflict -> "Course name already exists")
                      case Success(false) =>
                        val id = java.util.UUID.randomUUID().toString
                        val insert = courses += Course(id, req.name, uid, name, req.time, req.location)
                        onComplete(db.run(insert)) {
                          case Success(_) => complete(StatusCodes.OK -> Map("courseId" -> id))
                          case Failure(ex) => throw ex
                        }
                      case Failure(ex) => throw ex
                    }
                  case Success(Some((_, role, _))) =>
                    complete(StatusCodes.Forbidden -> s"$role not allowed to create courses")
                  case _ => complete(StatusCodes.Unauthorized)
                }
              }
            }
          },
          path("update") {
            post {
              entity(as[UpdateCourseRequest]) { req =>
                onComplete(extractUidAndRole(auth)) {
                  case Success(Some((uid, "teacher", _))) =>
                    val q = courses.filter(c => c.id === req.courseId && c.uid === uid)
                      .map(c => (c.name, c.time, c.location))
                      .update((req.name, req.time, req.location))
                    onComplete(db.run(q)) {
                      case Success(0) => complete(StatusCodes.NotFound -> "Course not found or not owned")
                      case Success(_) => complete("Updated")
                      case Failure(e) => throw e
                    }
                  case _ => complete(StatusCodes.Forbidden)
                }
              }
            }
          },
          path("delete") {
            post {
              entity(as[DeleteCourseRequest]) { req =>
                onComplete(extractUidAndRole(auth)) {
                  case Success(Some((uid, "teacher", _))) =>
                    val q = courses.filter(c => c.id === req.courseId && c.uid === uid).delete
                    onComplete(db.run(q)) {
                      case Success(0) => complete(StatusCodes.NotFound -> "Course not found or not owned")
                      case Success(_) => complete("Deleted")
                      case Failure(e) => throw e
                    }
                  case _ => complete(StatusCodes.Forbidden)
                }
              }
            }
          },
          path("select") {
            post {
              entity(as[EnrollmentRequest]) { req =>
                onComplete(extractUidAndRole(auth)) {
                  case Success(Some((uid, "student", _))) =>
                    val courseExistsF = db.run(courses.filter(_.id === req.courseId).exists.result)
                    val alreadySelectedF = db.run(enrollments.filter(e => e.uid === uid && e.courseId === req.courseId).exists.result)

                    onComplete(courseExistsF.flatMap { courseExists =>
                      if (!courseExists) {
                        Future.failed(new Exception("Course not found"))
                      } else {
                        alreadySelectedF.flatMap { selected =>
                          if (selected) Future.failed(new Exception("Already selected"))
                          else db.run(enrollments += Enrollment(uid, req.courseId))
                        }
                      }
                    }) {
                      case Success(_) => complete("Selected")
                      case Failure(e) if e.getMessage == "Course not found" => complete(StatusCodes.NotFound -> e.getMessage)
                      case Failure(e) if e.getMessage == "Already selected" => complete(StatusCodes.Conflict -> e.getMessage)
                      case Failure(e) => throw e
                    }

                  case _ => complete(StatusCodes.Forbidden)
                }
              }
            }
          },
          path("drop") {
            post {
              entity(as[DropRequest]) { req =>
                onComplete(extractUidAndRole(auth)) {
                  case Success(Some((uid, "student", _))) =>
                    val del = enrollments.filter(e => e.uid === uid && e.courseId === req.courseId).delete
                    onComplete(db.run(del)) {
                      case Success(0) => complete(StatusCodes.NotFound -> "No enrollment record")
                      case Success(_) => complete("Dropped")
                      case Failure(e) => throw e
                    }
                  case _ => complete(StatusCodes.Forbidden)
                }
              }
            }
          }
        )
      } ~
      path("list") {
        get {
          onComplete(db.run(courses.map(_.id).result)) {
            case Success(ids) => complete(ids.asJson)
            case Failure(ex) => throw ex
          }
        }
      } ~
      path("info") {
        get {
          parameter("id") { courseId =>
            val query = courses.filter(_.id === courseId).result.headOption
            onComplete(db.run(query)) {
              case Success(Some(c)) =>
                val json = Map(
                  "name" -> c.name,
                  "teacherName" -> c.teacherName,
                  "time" -> c.time,
                  "location" -> c.location
                ).asJson
                complete(json)
              case Success(None) => complete(StatusCodes.NotFound -> "Course not found")
              case Failure(ex) => throw ex
            }
          }
        }
      } ~
      path("check") {
        post {
          entity(as[CheckRequest]) { req =>
            val infoUrl = Uri(Main.userInfoUrl).withQuery(Uri.Query("uid" -> req.uid))
            val infoReq = HttpRequest(uri = infoUrl)
            onComplete(Http().singleRequest(infoReq)) {
              case Success(res) if res.status == StatusCodes.OK =>
                onComplete(res.entity.toStrict(3.seconds)) {
                  case Success(strictEntity) =>
                    val json = parse(strictEntity.data.utf8String).getOrElse(io.circe.Json.Null)
                    val role = json.hcursor.get[String]("type").getOrElse("")

                    role match {
                      case "student" =>
                        val q = enrollments.filter(e => e.uid === req.uid && e.courseId === req.courseId).exists.result
                        onComplete(db.run(q)) {
                          case Success(true) => complete("Selected")
                          case Success(false) => complete("Not selected")
                          case Failure(e) => throw e
                        }

                      case "teacher" =>
                        val q = courses.filter(c => c.id === req.courseId && c.uid === req.uid).exists.result
                        onComplete(db.run(q)) {
                          case Success(true) => complete("Created")
                          case Success(false) => complete("Not created")
                          case Failure(e) => throw e
                        }

                      case _ =>
                        complete(StatusCodes.BadRequest -> "Invalid role")
                    }

                  case Failure(e) =>
                    throw e
                }

              case Success(res) =>
                complete(StatusCodes.NotFound -> "User not found")

              case Failure(e) =>
                throw e
            }
          }
        }
      }
    }
  }
}

object Boot extends App {
  import Main._
  Http().newServerAt("localhost", 8082).bind(routes)
  Await.ready(Future.never, Duration.Inf)
}
