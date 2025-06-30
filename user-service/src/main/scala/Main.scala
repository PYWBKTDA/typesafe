package user

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{ExceptionHandler, Route}
import akka.http.scaladsl.server.Directives._
import akka.stream.Materializer
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe._
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.parser._
import pdi.jwt._
import slick.jdbc.PostgresProfile.api._
import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success}
import com.github.t3hnar.bcrypt._

sealed trait UserInfo
case class Student(studentId: String, name: String, department: String) extends UserInfo
case class Teacher(teacherId: String, name: String, department: String) extends UserInfo
object UserInfo {
  implicit val encodeUserInfo: Encoder[UserInfo] = Encoder.instance {
    case s: Student => s.asJson.deepMerge(Json.obj("type" -> Json.fromString("student")))
    case t: Teacher => t.asJson.deepMerge(Json.obj("type" -> Json.fromString("teacher")))
  }
  implicit val decodeUserInfo: Decoder[UserInfo] = Decoder.instance { c =>
    c.downField("type").as[String].flatMap {
      case "student" => c.as[Student]
      case "teacher" => c.as[Teacher]
      case other     => Left(DecodingFailure(s"Unknown type: $other", c.history))
    }
  }
}

case class User(uid: String, username: String, password: String, info: UserInfo)
case class RegisterRequest(username: String, password: String, confirmPassword: String, info: UserInfo)
case class LoginRequest(username: String, password: String)
case class UpdateRequest(oldPassword: Option[String], newPassword: Option[String], confirmPassword: Option[String], info: Option[UserInfo])

class UserTable(tag: Tag) extends Table[User](tag, "users") {
  def uid = column[String]("uid", O.PrimaryKey)
  def username = column[String]("username", O.Unique)
  def password = column[String]("password")
  def info = column[String]("info")
  def * = (uid, username, password, info).<>( 
    { case (u,n,p,i) =>
      val json = parse(i).getOrElse(Json.Null)
      val info = json.as[UserInfo].getOrElse(Student("","",""))
      User(u,n,p,info)
    },
    (u: User) => Some((u.uid, u.username, u.password, u.info.asJson.noSpaces))
  )
}

object Main {
  implicit val system: ActorSystem = ActorSystem("user-system")
  implicit val materializer: Materializer = Materializer(system)
  implicit val ec: ExecutionContextExecutor = system.dispatcher

  val db = Database.forConfig("db")
  val users = TableQuery[UserTable]

  val jwtKey = "secret"
  def generateToken(uid: String): String = JwtCirce.encode(JwtClaim(subject = Some(uid)), jwtKey, JwtAlgorithm.HS256)
  def verifyToken(token: String): Option[String] = JwtCirce.decode(token, jwtKey, Seq(JwtAlgorithm.HS256)).toOption.flatMap(_.subject)

  implicit val exceptionHandler: ExceptionHandler = ExceptionHandler {
    case ex: Throwable => extractUri { _ => complete(StatusCodes.InternalServerError -> ex.getMessage) }
  }

  val routes: Route = handleExceptions(exceptionHandler) {
    pathPrefix("user") {
      path("register") {
        post {
          entity(as[RegisterRequest]) { req =>
            if (req.password != req.confirmPassword) complete(StatusCodes.BadRequest -> "Passwords do not match")
            else {
              val action = users.filter(_.username === req.username).result.headOption.flatMap {
                case Some(_) => DBIO.successful(Left("Username exists"))
                case None =>
                  val id = java.util.UUID.randomUUID().toString
                  val hashed = req.password.bcrypt
                  val u = User(id, req.username, hashed, req.info)
                  (users += u).map(_ => Right("Registered"))
              }.transactionally
              onComplete(db.run(action)) {
                case Success(Right(msg)) => complete(StatusCodes.OK -> msg)
                case Success(Left(err))  => complete(StatusCodes.Conflict -> err)
                case Failure(ex)         => throw ex
              }
            }
          }
        }
      } ~
      path("login") {
        post {
          entity(as[LoginRequest]) { req =>
            onComplete(db.run(users.filter(_.username === req.username).result.headOption)) {
              case Success(Some(user)) if req.password.isBcrypted(user.password) =>
                complete(StatusCodes.OK -> Map("token" -> generateToken(user.uid)))
              case Success(_) =>
                complete(StatusCodes.Unauthorized -> "Invalid credentials")
              case Failure(ex) =>
                throw ex
            }
          }
        }
      } ~
      path("update") {
        post {
          headerValueByName("Authorization") { token =>
            verifyToken(token) match {
              case Some(uid) =>
                onComplete(db.run(users.filter(_.uid === uid).result.headOption)) {
                  case Success(Some(user)) =>
                    entity(as[UpdateRequest]) { req =>
                      val fut: Future[Route] = (req.oldPassword, req.newPassword, req.confirmPassword) match {
                        case (Some(oldP), Some(newP), Some(conf)) =>
                          if (!oldP.isBcrypted(user.password)) Future.successful(complete(StatusCodes.Unauthorized -> "Old password incorrect"))
                          else if (newP != conf) Future.successful(complete(StatusCodes.BadRequest -> "New passwords do not match"))
                          else {
                            val upd = user.copy(password = newP.bcrypt, info = req.info.getOrElse(user.info))
                            db.run(users.filter(_.uid === uid).update(upd)).map(_ => complete(StatusCodes.OK -> "Updated with password"))
                          }
                        case _ =>
                          req.info match {
                            case Some(i) =>
                              val upd = user.copy(info = i)
                              db.run(users.filter(_.uid === uid).update(upd)).map(_ => complete(StatusCodes.OK -> "Info updated"))
                            case None =>
                              Future.successful(complete(StatusCodes.BadRequest -> "Nothing to update"))
                          }
                      }
                      onComplete(fut) {
                        case Success(route) => route
                        case Failure(ex)    => throw ex
                      }
                    }
                  case Success(None) => complete(StatusCodes.NotFound -> "User not found")
                  case Failure(ex)   => throw ex
                }
              case None => complete(StatusCodes.Unauthorized -> "Invalid token")
            }
          }
        }
      } ~
      path("info") {
        get {
          parameters("uid") { uid =>
            headerValueByName("Authorization") { token =>
              verifyToken(token) match {
                case Some(_) =>
                  onComplete(db.run(users.filter(_.uid === uid).result.headOption)) {
                    case Success(Some(user)) => complete(StatusCodes.OK -> user.info.asJson)
                    case Success(None) => complete(StatusCodes.NotFound -> "User not found")
                    case Failure(ex) => throw ex
                  }
                case None => complete(StatusCodes.Unauthorized -> "Invalid token")
              }
            }
          }
        }
      }
    }
  }
}

object Boot extends App {
  import Main._
  Http().newServerAt("localhost", 8081).bind(routes)
  Await.ready(Future.never, Duration.Inf)
}
