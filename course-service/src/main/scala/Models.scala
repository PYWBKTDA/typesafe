package course

import io.circe._
import org.http4s.circe._
import org.http4s.EntityDecoder
import org.http4s.EntityEncoder
import cats.effect.IO

case class Course(id: String, name: String, uid: String, teacherName: String, time: String, location: String)
case class Enrollment(uid: String, courseId: String)

case class CreateCourseRequest(name: String, time: String, location: String)
case class UpdateCourseRequest(courseId: String, name: String, time: String, location: String)
case class DeleteCourseRequest(courseId: String)
case class EnrollmentRequest(courseId: String)
case class DropRequest(courseId: String)
case class CheckRequest(courseId: String)

sealed trait AppError { def msg: String }
case class ValidationError(msg: String) extends AppError
case class AuthError(msg: String) extends AppError
case class NotFoundError(msg: String) extends AppError

object Codecs {
  implicit def decoder[A: Decoder]: EntityDecoder[IO, A] = jsonOf[IO, A]
  implicit def encoder[A: Encoder]: EntityEncoder[IO, A] = jsonEncoderOf[IO, A]
}
