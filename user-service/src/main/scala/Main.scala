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
case class UpdateRequest(oldPassword: String, newPassword: String, confirmPassword: String, info: UserInfo)

class UserTable(tag: Tag) extends Table[User](tag, "users") {
  def uid = column[String]("uid", O.PrimaryKey)
  def username = column[String]("username", O.Unique)
  def password = column[String]("password")
  def info = column[String]("info")
  def * = (uid, username, password, info).<>(
    { case (u, n, p, i) =>
      val json = parse(i).getOrElse(Json.Null)
      val info = json.as[UserInfo].getOrElse(Student("", "", ""))
      User(u, n, p, info)
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
		println("fuck")
		db.run(sql"SELECT 1".as[Int]).map(println)
		println("fuck")
    pathPrefix("user") {
			println("fuck user")
      path("register") {
	 			println("fuck register")
  	    post {
          entity(as[RegisterRequest]) { req =>
			 			println("fuck register xixi")
						println(req.asJson.spaces2)
            if (req.username.trim.isEmpty || req.password.trim.isEmpty)
              complete(StatusCodes.BadRequest -> "Username or password cannot be empty")
            else if (req.password != req.confirmPassword)
              complete(StatusCodes.BadRequest -> "Passwords do not match")
            else {
              val id = java.util.UUID.randomUUID().toString
              val action = users.filter(_.username === req.username).result.headOption.flatMap {
                case Some(_) => DBIO.successful(Left("Username exists"))
                case None =>
                  val hashed = req.password.bcrypt
                  val u = User(id, req.username, hashed, req.info)
                  (users += u).map(_ => Right("Registered"))
              }.transactionally
				 			println("fuck register xixi")
							
              onComplete(db.run(action)) {
								case Success(Right(msg)) =>
									val token = generateToken(id) 
									val role = req.info match {
										case _: Student => "student"
										case _: Teacher => "teacher"
									}
									complete(StatusCodes.OK -> Json.obj(
										"token" -> Json.fromString(token),
										"username" -> Json.fromString(req.username),
										"role" -> Json.fromString(role)
									))

								case Success(Left(err)) =>
									complete(StatusCodes.Conflict -> err)

								case Failure(ex) =>
									throw ex
							}
            }
          }
        }
      } ~
      path("login") {
        post {
          entity(as[LoginRequest]) { req =>
            if (req.username.trim.isEmpty || req.password.trim.isEmpty)
              complete(StatusCodes.BadRequest -> "Username or password cannot be empty")
            else {
							onComplete(db.run(users.filter(_.username === req.username).result.headOption)) {
								case Success(Some(user)) if req.password.isBcrypted(user.password) =>
									val token = generateToken(user.uid)
									val role = user.info match {
										case _: Student => "student"
										case _: Teacher => "teacher"
									}
									complete(StatusCodes.OK -> Json.obj(
										"token" -> Json.fromString(token),
										"username" -> Json.fromString(user.username),
										"role" -> Json.fromString(role)
									))

								case Success(_) =>
									complete(StatusCodes.Unauthorized -> "Invalid credentials")

								case Failure(ex) =>
									throw ex
							}
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
                      val hasPasswordUpdate = req.oldPassword.nonEmpty || req.newPassword.nonEmpty || req.confirmPassword.nonEmpty
                      if (!hasPasswordUpdate) {
                        val updatedUser = user.copy(info = req.info)
                        onComplete(db.run(users.filter(_.uid === uid).update(updatedUser))) {
                          case Success(_) => complete(StatusCodes.OK -> "Info updated")
                          case Failure(ex) => throw ex
                        }
                      } else {
                        if (req.oldPassword.isEmpty || req.newPassword.isEmpty || req.confirmPassword.isEmpty)
                          complete(StatusCodes.BadRequest -> "Incomplete password fields")
                        else if (!req.oldPassword.isBcrypted(user.password))
                          complete(StatusCodes.Unauthorized -> "Old password incorrect")
                        else if (req.newPassword != req.confirmPassword)
                          complete(StatusCodes.BadRequest -> "New passwords do not match")
                        else {
                          val updatedUser = user.copy(
                            password = req.newPassword.bcrypt,
                            info = req.info
                          )
                          onComplete(db.run(users.filter(_.uid === uid).update(updatedUser))) {
                            case Success(_) => complete(StatusCodes.OK -> "Info and password updated")
                            case Failure(ex) => throw ex
                          }
                        }
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
      path("uid") {
        get {
          headerValueByName("Authorization") { token =>
            verifyToken(token) match {
              case Some(uid) => complete(StatusCodes.OK -> Json.obj("uid" -> Json.fromString(uid)))
              case None      => complete(StatusCodes.Unauthorized -> "Invalid token")
            }
          }
        }
      } ~
      path("info") {
        get {
          parameters("uid") { uid =>
            onComplete(db.run(users.filter(_.uid === uid).result.headOption)) {
              case Success(Some(user)) => complete(StatusCodes.OK -> user.info.asJson)
              case Success(None)       => complete(StatusCodes.NotFound -> "User not found")
              case Failure(ex)         => throw ex
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
