package user

import io.circe._
import io.circe.generic.semiauto._

sealed trait UserInfo
case class Student(studentId: String, name: String, department: String) extends UserInfo
case class Teacher(teacherId: String, name: String, department: String) extends UserInfo

case class User(uid: String, username: String, password: String, info: UserInfo)
case class RegisterRequest(username: String, password: String, confirmPassword: String, info: UserInfo)
case class LoginRequest(username: String, password: String)
case class UpdateRequest(oldPassword: Option[String], newPassword: Option[String], confirmPassword: Option[String], info: UserInfo)

sealed trait AppError { def msg: String }
case class ValidationError(msg: String) extends AppError
case class AuthError(msg: String) extends AppError
case class NotFoundError(msg: String) extends AppError

object Codecs {
  implicit val studentDecoder: Decoder[Student] = deriveDecoder
  implicit val studentEncoder: Encoder[Student] = deriveEncoder
  implicit val teacherDecoder: Decoder[Teacher] = deriveDecoder
  implicit val teacherEncoder: Encoder[Teacher] = deriveEncoder

  implicit val userInfoDecoder: Decoder[UserInfo] = Decoder.instance { c =>
    c.downField("type").as[String].flatMap {
      case "student" => c.as[Student]
      case "teacher" => c.as[Teacher]
      case other     => Left(DecodingFailure(s"Unknown type: $other", c.history))
    }
  }

  implicit val userInfoEncoder: Encoder[UserInfo] = Encoder.instance {
    case s: Student => studentEncoder(s).deepMerge(Json.obj("type" -> Json.fromString("student")))
    case t: Teacher => teacherEncoder(t).deepMerge(Json.obj("type" -> Json.fromString("teacher")))
  }

  implicit val userDecoder: Decoder[User] = deriveDecoder
  implicit val userEncoder: Encoder[User] = deriveEncoder
  implicit val regDecoder: Decoder[RegisterRequest] = deriveDecoder
  implicit val logDecoder: Decoder[LoginRequest] = deriveDecoder
  implicit val updDecoder: Decoder[UpdateRequest] = deriveDecoder
}
