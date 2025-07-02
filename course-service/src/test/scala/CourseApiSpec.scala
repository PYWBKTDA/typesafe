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

  "Course API full test" should {

    "generate tokens" in {
      teacherToken = generateToken(teacherId)
      studentToken = generateToken(studentId)
    }

    "create course as teacher" in {
      val req = CreateCourseRequest("Math", "Mon 10am", "Room A")
      Post("/course/create", req) ~> bearer(teacherToken) ~> routes ~> check {
        status shouldBe StatusCodes.OK
        val res = responseAs[Map[String, String]]
        courseId = res("courseId")
      }
    }

    "fail to create course as student" in {
      val req = CreateCourseRequest("Physics", "Tue 2pm", "Room B")
      Post("/course/create", req) ~> bearer(studentToken) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    "fail to create duplicate course" in {
      val req = CreateCourseRequest("Math", "Mon 10am", "Room A")
      Post("/course/create", req) ~> bearer(teacherToken) ~> routes ~> check {
        status shouldBe StatusCodes.Conflict
      }
    }

    "update course as teacher" in {
      val req = UpdateCourseRequest(courseId, "Math 2", "Wed 3pm", "Room C")
      Post("/course/update", req) ~> bearer(teacherToken) ~> routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[String] shouldBe "Updated"
      }
    }

    "fail to update course as student" in {
      val req = UpdateCourseRequest(courseId, "Math X", "Fri 1pm", "Room D")
      Post("/course/update", req) ~> bearer(studentToken) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    "select course as student" in {
      val req = EnrollmentRequest(courseId)
      Post("/course/select", req) ~> bearer(studentToken) ~> routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[String] shouldBe "Selected"
      }
    }

    "fail to select course again" in {
      val req = EnrollmentRequest(courseId)
      Post("/course/select", req) ~> bearer(studentToken) ~> routes ~> check {
        status shouldBe StatusCodes.Conflict
        responseAs[String] shouldBe "Already selected"
      }
    }

    "fail to select as teacher" in {
      val req = EnrollmentRequest(courseId)
      Post("/course/select", req) ~> bearer(teacherToken) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    "fail to select non-existent course" in {
      val req = EnrollmentRequest("non-existent")
      Post("/course/select", req) ~> bearer(studentToken) ~> routes ~> check {
        status shouldBe StatusCodes.NotFound
        responseAs[String] shouldBe "Course not found"
      }
    }

    "drop course as student" in {
      val req = DropRequest(courseId)
      Post("/course/drop", req) ~> bearer(studentToken) ~> routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[String] shouldBe "Dropped"
      }
    }

    "fail to drop again" in {
      val req = DropRequest(courseId)
      Post("/course/drop", req) ~> bearer(studentToken) ~> routes ~> check {
        status shouldBe StatusCodes.NotFound
        responseAs[String] shouldBe "No enrollment record"
      }
    }

    "fail to drop as teacher" in {
      val req = DropRequest(courseId)
      Post("/course/drop", req) ~> bearer(teacherToken) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    "check teacher ownership" in {
      val req = CheckRequest(courseId, teacherId)
      Post("/course/check", req.asJson) ~> routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[String] shouldBe "Created"
      }
    }

    "check student selection (false)" in {
      val req = CheckRequest(courseId, studentId)
      Post("/course/check", req.asJson) ~> routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[String] shouldBe "Not selected"
      }
    }

    "check unknown uid" in {
      val req = CheckRequest(courseId, "nonexistent-uid")
      Post("/course/check", req.asJson) ~> routes ~> check {
        status shouldBe StatusCodes.NotFound
        responseAs[String] shouldBe "User not found"
      }
    }

    "get course info" in {
      Get(s"/course/info?id=$courseId") ~> routes ~> check {
        status shouldBe StatusCodes.OK
        val map = responseAs[Map[String, String]]
        map("name") shouldBe "Math 2"
      }
    }

    "fail to get info for unknown id" in {
      Get(s"/course/info?id=unknown") ~> routes ~> check {
        status shouldBe StatusCodes.NotFound
        responseAs[String] shouldBe "Course not found"
      }
    }

    "get course list" in {
      Get("/course/list") ~> routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[Vector[String]].contains(courseId) shouldBe true
      }
    }

    "delete course as teacher" in {
      val req = DeleteCourseRequest(courseId)
      Post("/course/delete", req) ~> bearer(teacherToken) ~> routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[String] shouldBe "Deleted"
      }
    }

    "fail to delete again" in {
      val req = DeleteCourseRequest(courseId)
      Post("/course/delete", req) ~> bearer(teacherToken) ~> routes ~> check {
        status shouldBe StatusCodes.NotFound
        responseAs[String] shouldBe "Course not found or not owned"
      }
    }

    "fail to delete as student" in {
      val req = DeleteCourseRequest(courseId)
      Post("/course/delete", req) ~> bearer(studentToken) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }
  }
}
