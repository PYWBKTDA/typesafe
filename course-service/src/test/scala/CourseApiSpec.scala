package course

import scala.concurrent.Await
import scala.concurrent.duration._
import slick.jdbc.PostgresProfile.api._
import course.Main.db
import course.Main.courses
import course.Main.enrollments
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.server.Route
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._
import io.circe.syntax._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.BeforeAndAfterAll
import pdi.jwt.{JwtAlgorithm, JwtCirce, JwtClaim}

class CourseApiSpec extends AnyWordSpec with Matchers with ScalatestRouteTest with BeforeAndAfterAll {

  val routes: Route = Main.routes

  var teacherToken: String = ""
  var studentToken: String = ""
  val teacherId: String = "t-123"
  val studentId: String = "s-456"
  var courseId: String = ""

  def generateToken(uid: String): String =
    JwtCirce.encode(JwtClaim(subject = Some(uid)), "secret", JwtAlgorithm.HS256)

  def bearer(token: String): RawHeader = RawHeader("Authorization", token)

  override def beforeAll(): Unit = {
    Await.result(db.run(DBIO.seq(
      enrollments.delete,
      courses.delete
    )), 5.seconds)
  }

  "Course API full test (with real user-service)" should {

    "generate tokens" in {
      teacherToken = generateToken(teacherId)
      studentToken = generateToken(studentId)
    }

    "fail to create course as student" in {
      val req = CreateCourseRequest("Math", "Mon 10am", "Room A")
      Post("/course/create", req) ~> bearer(studentToken) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    "create course as teacher" in {
      val req = CreateCourseRequest("Math", "Mon 10am", "Room A")
      Post("/course/create", req) ~> bearer(teacherToken) ~> routes ~> check {
        status shouldBe StatusCodes.OK
        val res = responseAs[Map[String, String]]
        courseId = res("courseId")
      }
    }

    "fail to create duplicate course" in {
      val req = CreateCourseRequest("Math", "Mon 10am", "Room A")
      Post("/course/create", req) ~> bearer(teacherToken) ~> routes ~> check {
        status shouldBe StatusCodes.Conflict
      }
    }

    "fail to update course as student" in {
      val req = UpdateCourseRequest(courseId, "Math 2", "Tue 2pm", "Room B")
      Post("/course/update", req) ~> bearer(studentToken) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    "update course as teacher" in {
      val req = UpdateCourseRequest(courseId, "Math 2", "Tue 2pm", "Room B")
      Post("/course/update", req) ~> bearer(teacherToken) ~> routes ~> check {
        status shouldBe StatusCodes.OK
      }
    }

    "fail to select course as teacher" in {
      val req = EnrollmentRequest(courseId)
      Post("/course/select", req) ~> bearer(teacherToken) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    "fail to select non-existent course" in {
      val req = EnrollmentRequest("invalid-id")
      Post("/course/select", req) ~> bearer(studentToken) ~> routes ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }

    "select course as student" in {
      val req = EnrollmentRequest(courseId)
      Post("/course/select", req) ~> bearer(studentToken) ~> routes ~> check {
        status shouldBe StatusCodes.OK
      }
    }

    "fail to select again" in {
      val req = EnrollmentRequest(courseId)
      Post("/course/select", req) ~> bearer(studentToken) ~> routes ~> check {
        status shouldBe StatusCodes.Conflict
      }
    }

    "drop course" in {
      val req = DropRequest(courseId)
      Post("/course/drop", req) ~> bearer(studentToken) ~> routes ~> check {
        status shouldBe StatusCodes.OK
      }
    }

    "fail to drop again" in {
      val req = DropRequest(courseId)
      Post("/course/drop", req) ~> bearer(studentToken) ~> routes ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }

    "fail to delete course as student" in {
      val req = DeleteCourseRequest(courseId)
      Post("/course/delete", req) ~> bearer(studentToken) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    "delete course as teacher" in {
      val req = DeleteCourseRequest(courseId)
      Post("/course/delete", req) ~> bearer(teacherToken) ~> routes ~> check {
        status shouldBe StatusCodes.OK
      }
    }

    "fail to delete again" in {
      val req = DeleteCourseRequest(courseId)
      Post("/course/delete", req) ~> bearer(teacherToken) ~> routes ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }

    "create another course for listing" in {
      val req = CreateCourseRequest("History", "Wed 1pm", "Room C")
      Post("/course/create", req) ~> bearer(teacherToken) ~> routes ~> check {
        status shouldBe StatusCodes.OK
        val res = responseAs[Map[String, String]]
        courseId = res("courseId")
      }
    }

    "list all courses" in {
      Get("/course/list") ~> routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[Vector[Course]].exists(_.name == "History") shouldBe true
      }
    }

    "check teacher ownership" in {
      val req = CheckRequest(courseId, teacherId, "teacher")
      Post("/course/check", req.asJson) ~> routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[String] shouldBe "Created"
      }
    }

    "check student selection (false)" in {
      val req = CheckRequest(courseId, studentId, "student")
      Post("/course/check", req.asJson) ~> routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[String] shouldBe "Not selected"
      }
    }

    "fail check with invalid role" in {
      val req = CheckRequest(courseId, studentId, "admin")
      Post("/course/check", req.asJson) ~> routes ~> check {
        status shouldBe StatusCodes.BadRequest
      }
    }
  }
}
