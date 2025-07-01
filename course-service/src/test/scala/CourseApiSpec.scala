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
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.BeforeAndAfterAll
import pdi.jwt.{JwtAlgorithm, JwtCirce, JwtClaim}
import scala.concurrent.Future

class CourseApiSpec extends AnyWordSpec with Matchers with ScalatestRouteTest with BeforeAndAfterAll {

  val routes: Route = Main.routes

  var teacherToken: String = ""
  var studentToken: String = ""
  var teacherId: String = ""
  var studentId: String = ""
  var courseId: String = ""

  def generateToken(uid: String): String = {
    JwtCirce.encode(JwtClaim(subject = Some(uid)), "secret", JwtAlgorithm.HS256)
  }

  def bearer(token: String): RawHeader = RawHeader("Authorization", s"Bearer $token")

  val mockUserServiceRoutes: Route =
    path("user" / "info") {
      get {
        parameter("uid") { uid =>
          headerValueByName("Authorization") { _ =>
            val json =
              if (uid == "t-123") """{ "teacherId": "t-123", "name": "T", "department": "CS", "type": "teacher" }"""
              else if (uid == "s-456") """{ "studentId": "s-456", "name": "S", "department": "CS", "type": "student" }"""
              else """{}"""
            complete(HttpEntity(ContentTypes.`application/json`, json))
          }
        }
      }
    }

  override def beforeAll(): Unit = {
    Await.result(db.run(DBIO.seq(
      enrollments.delete,
      courses.delete
    )), 5.seconds)
    Await.result(
      Http().newServerAt("localhost", 8081).bind(mockUserServiceRoutes),
      3.seconds
    )
    super.beforeAll()
  }

  "Course API" should {

    "mock teacher and student tokens" in {
      teacherId = "t-123"
      studentId = "s-456"
      teacherToken = generateToken(teacherId)
      studentToken = generateToken(studentId)
    }

    "fail to create course if not teacher" in {
      val req = CreateCourseRequest("Math")
      Post("/course/create", req) ~> bearer(studentToken) ~> routes ~> check {
        status shouldBe StatusCodes.Unauthorized
      }
    }

    "create course successfully" in {
      val req = CreateCourseRequest("Math")
      Post("/course/create", req) ~> bearer(teacherToken) ~> routes ~> check {
        status shouldBe StatusCodes.OK
        val res = responseAs[Map[String, String]]
        courseId = res("courseId")
        courseId.nonEmpty shouldBe true
      }
    }

    "fail to create duplicate course" in {
      val req = CreateCourseRequest("Math")
      Post("/course/create", req) ~> bearer(teacherToken) ~> routes ~> check {
        status shouldBe StatusCodes.Conflict
        responseAs[String] should include("already created")
      }
    }

    "fail to select non-existent course" in {
      val req = EnrollmentRequest("non-existent-id")
      Post("/course/select", req) ~> bearer(studentToken) ~> routes ~> check {
        status shouldBe StatusCodes.BadRequest
        responseAs[String] should include("does not exist")
      }
    }

    "select course successfully" in {
      val req = EnrollmentRequest(courseId)
      Post("/course/select", req) ~> bearer(studentToken) ~> routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[String] shouldBe "Selected"
      }
    }

    "fail to select already selected course" in {
      val req = EnrollmentRequest(courseId)
      Post("/course/select", req) ~> bearer(studentToken) ~> routes ~> check {
        status shouldBe StatusCodes.Conflict
        responseAs[String] should include("Already selected")
      }
    }

    "drop course successfully" in {
      val req = DropRequest(courseId)
      Post("/course/drop", req) ~> bearer(studentToken) ~> routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[String] shouldBe "Dropped"
      }
    }

    "fail to drop course again (already dropped)" in {
      val req = DropRequest(courseId)
      Post("/course/drop", req) ~> bearer(studentToken) ~> routes ~> check {
        status shouldBe StatusCodes.BadRequest
        responseAs[String] should include("No enrollment record")
      }
    }

    "delete course successfully by teacher" in {
      val req = DeleteCourseRequest(courseId)
      Post("/course/delete", req) ~> bearer(teacherToken) ~> routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[String] shouldBe "Deleted"
      }
    }

    "fail to delete course again (already deleted)" in {
      val req = DeleteCourseRequest(courseId)
      Post("/course/delete", req) ~> bearer(teacherToken) ~> routes ~> check {
        status shouldBe StatusCodes.Unauthorized
      }
    }

    "create another course for listing" in {
      val req = CreateCourseRequest("History")
      Post("/course/create", req) ~> bearer(teacherToken) ~> routes ~> check {
        status shouldBe StatusCodes.OK
        val res = responseAs[Map[String, String]]
        courseId = res("courseId")
        courseId.nonEmpty shouldBe true
      }
    }

    "list all courses" in {
      Get("/course/list") ~> routes ~> check {
        status shouldBe StatusCodes.OK
        val result = responseAs[Vector[Course]]
        result.exists(_.name == "History") shouldBe true
      }
    }

    "should search course by name (case-insensitive match)" in {
      Get("/course/list?name=hist") ~> routes ~> check {
        status shouldBe StatusCodes.OK
        val result = responseAs[Vector[Course]]
        result.exists(_.name.toLowerCase.contains("hist")) shouldBe true
      }
    }

    "return empty list when no course matches" in {
      Get("/course/list?name=nonexistent_course_987654321") ~> routes ~> check {
        status shouldBe StatusCodes.OK
        val result = responseAs[Vector[Course]]
        result shouldBe empty
      }
    }
  }
}
