package teaching

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{ExceptionHandler, Route}
import akka.http.scaladsl.server.Directives._
import akka.stream.Materializer
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._
import slick.jdbc.PostgresProfile.api._
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import scala.util.{Failure, Success}

case class Announcement(id: String, courseId: String, title: String, content: String)
case class Material(id: String, courseId: String, name: String, url: String)
case class Discussion(id: String, courseId: String, authorId: String, content: String)
case class Assignment(id: String, courseId: String, title: String, description: String)

case class CreateAnnouncement(courseId: String, title: String, content: String)
case class CreateMaterial(courseId: String, name: String, url: String)
case class CreateDiscussion(courseId: String, content: String)
case class CreateAssignment(courseId: String, title: String, description: String)

class AnnouncementTable(tag: Tag) extends Table[Announcement](tag, "announcements") {
  def id = column[String]("id", O.PrimaryKey)
  def courseId = column[String]("course_id")
  def title = column[String]("title")
  def content = column[String]("content")
  def * = (id, courseId, title, content) <> (Announcement.tupled, Announcement.unapply)
}

class MaterialTable(tag: Tag) extends Table[Material](tag, "materials") {
  def id = column[String]("id", O.PrimaryKey)
  def courseId = column[String]("course_id")
  def name = column[String]("name")
  def url = column[String]("url")
  def * = (id, courseId, name, url) <> (Material.tupled, Material.unapply)
}

class DiscussionTable(tag: Tag) extends Table[Discussion](tag, "discussions") {
  def id = column[String]("id", O.PrimaryKey)
  def courseId = column[String]("course_id")
  def authorId = column[String]("author_id")
  def content = column[String]("content")
  def * = (id, courseId, authorId, content) <> (Discussion.tupled, Discussion.unapply)
}

class AssignmentTable(tag: Tag) extends Table[Assignment](tag, "assignments") {
  def id = column[String]("id", O.PrimaryKey)
  def courseId = column[String]("course_id")
  def title = column[String]("title")
  def description = column[String]("description")
  def * = (id, courseId, title, description) <> (Assignment.tupled, Assignment.unapply)
}

object Main {
  implicit val system: ActorSystem = ActorSystem("teaching-system")
  implicit val materializer: Materializer = Materializer(system)
  implicit val ec: ExecutionContextExecutor = system.dispatcher

  val db = Database.forConfig("db")
  val announcements = TableQuery[AnnouncementTable]
  val materials = TableQuery[MaterialTable]
  val discussions = TableQuery[DiscussionTable]
  val assignments = TableQuery[AssignmentTable]

  val userUidUrl = "http://localhost:8081/user/uid"

  def extractUid(token: String): Future[Option[String]] = {
    val req = akka.http.scaladsl.model.HttpRequest(uri = userUidUrl).withHeaders(headers.RawHeader("Authorization", token))
    Http().singleRequest(req).flatMap { res =>
      if (res.status != StatusCodes.OK) Future.successful(None)
      else res.entity.toStrict(3.seconds).map { entity =>
        parse(entity.data.utf8String).flatMap(_.hcursor.get[String]("uid")).toOption
      }
    }
  }

  implicit val exceptionHandler: ExceptionHandler = ExceptionHandler {
    case ex: Throwable => complete(StatusCodes.InternalServerError -> ex.getMessage)
  }

  val routes: Route = handleExceptions(exceptionHandler) {
    pathPrefix("teaching") {
      headerValueByName("Authorization") { token =>
        onSuccess(extractUid(token)) {
          case Some(uid) =>
            concat(
              path("announcement" / "create") {
                post {
                  entity(as[CreateAnnouncement]) { req =>
                    val id = java.util.UUID.randomUUID().toString
                    val a = Announcement(id, req.courseId, req.title, req.content)
                    onComplete(db.run(announcements += a)) {
                      case Success(_) => complete(StatusCodes.OK -> "Announcement created")
                      case Failure(e) => throw e
                    }
                  }
                }
              },
              path("material" / "create") {
                post {
                  entity(as[CreateMaterial]) { req =>
                    val id = java.util.UUID.randomUUID().toString
                    val m = Material(id, req.courseId, req.name, req.url)
                    onComplete(db.run(materials += m)) {
                      case Success(_) => complete(StatusCodes.OK -> "Material uploaded")
                      case Failure(e) => throw e
                    }
                  }
                }
              },
              path("discussion" / "create") {
                post {
                  entity(as[CreateDiscussion]) { req =>
                    val id = java.util.UUID.randomUUID().toString
                    val d = Discussion(id, req.courseId, uid, req.content)
                    onComplete(db.run(discussions += d)) {
                      case Success(_) => complete(StatusCodes.OK -> "Discussion posted")
                      case Failure(e) => throw e
                    }
                  }
                }
              },
              path("assignment" / "create") {
                post {
                  entity(as[CreateAssignment]) { req =>
                    val id = java.util.UUID.randomUUID().toString
                    val a = Assignment(id, req.courseId, req.title, req.description)
                    onComplete(db.run(assignments += a)) {
                      case Success(_) => complete(StatusCodes.OK -> "Assignment created")
                      case Failure(e) => throw e
                    }
                  }
                }
              }
            )
          case None => complete(StatusCodes.Unauthorized -> "Invalid token")
        }
      }
    }
  }
}

object Boot extends App {
  import Main._
  Http().newServerAt("localhost", 8083).bind(routes)
  Await.ready(Future.never, Duration.Inf)
}
