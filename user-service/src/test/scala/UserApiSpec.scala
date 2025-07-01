package user

import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.server.Route
import io.circe.generic.auto._
import io.circe.Json
import io.circe.syntax._
import io.circe.parser._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.BeforeAndAfterAll
import pdi.jwt.{JwtClaim, JwtCirce, JwtAlgorithm}
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.Await
import scala.concurrent.duration._

class UserApiSpec extends AnyWordSpec with Matchers with ScalatestRouteTest with BeforeAndAfterAll {

  val routes: Route = Main.routes

  var token: String = ""
  var uid: String = ""
  val username = "testuser"

  override def beforeAll(): Unit = {
    val db = Main.db
    val users = Main.users
    Await.result(db.run(users.delete), 5.seconds)
  }

  "User API" should {

    "register a new user successfully" in {
      val req = RegisterRequest(
        username,
        "123456",
        "123456",
        Student("S001", "Test User", "CS")
      )
      Post("/user/register", req.asJson) ~> routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[String] shouldBe "Registered"
      }
    }

    "fail to register with duplicate username" in {
      val req = RegisterRequest(
        username,
        "123456",
        "123456",
        Student("S001", "Test User", "CS")
      )
      Post("/user/register", req.asJson) ~> routes ~> check {
        status shouldBe StatusCodes.Conflict
        responseAs[String] shouldBe "Username exists"
      }
    }

    "fail to register with mismatched passwords" in {
      val req = RegisterRequest(
        "user2",
        "abc",
        "xyz",
        Student("S002", "Mismatch", "Math")
      )
      Post("/user/register", req.asJson) ~> routes ~> check {
        status shouldBe StatusCodes.BadRequest
        responseAs[String] shouldBe "Passwords do not match"
      }
    }

    "fail to register with empty username or password" in {
      val req = RegisterRequest(
        "",
        "",
        "",
        Student("S002", "Empty", "Math")
      )
      Post("/user/register", req.asJson) ~> routes ~> check {
        status shouldBe StatusCodes.BadRequest
        responseAs[String] shouldBe "Username or password cannot be empty"
      }
    }

    "login successfully and receive token" in {
      val req = LoginRequest(username, "123456")
      Post("/user/login", req.asJson) ~> routes ~> check {
        status shouldBe StatusCodes.OK
        val res = responseAs[Map[String, String]]
        res.contains("token") shouldBe true
        token = res("token")
        val claim = JwtCirce.decode(token, "secret", Seq(JwtAlgorithm.HS256)).get
        uid = claim.subject.get
      }
    }

    "fail to login with wrong password" in {
      val req = LoginRequest(username, "wrongpass")
      Post("/user/login", req.asJson) ~> routes ~> check {
        status shouldBe StatusCodes.Unauthorized
        responseAs[String] shouldBe "Invalid credentials"
      }
    }

    "fail to login with empty username or password" in {
      val req = LoginRequest("", "")
      Post("/user/login", req.asJson) ~> routes ~> check {
        status shouldBe StatusCodes.BadRequest
        responseAs[String] shouldBe "Username or password cannot be empty"
      }
    }

    "update user info only" in {
      val updateReq = UpdateRequest("", "", "", Student("S001", "New Name", "AI"))
      Post("/user/update", updateReq.asJson).withHeaders(RawHeader("Authorization", token)) ~> routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[String] shouldBe "Info updated"
      }

      Get(s"/user/info?uid=$uid") ~> routes ~> check {
        status shouldBe StatusCodes.OK
        val json = responseAs[Json]
        val name = json.hcursor.downField("name").as[String].getOrElse("")
        val dept = json.hcursor.downField("department").as[String].getOrElse("")
        name shouldBe "New Name"
        dept shouldBe "AI"
      }
    }

    "fail to update with missing password fields" in {
      val req = UpdateRequest("123456", "", "", Student("S001", "Still", "CS"))
      Post("/user/update", req.asJson).withHeaders(RawHeader("Authorization", token)) ~> routes ~> check {
        status shouldBe StatusCodes.BadRequest
        responseAs[String] shouldBe "Incomplete password fields"
      }
    }

    "fail to update password with wrong old password" in {
      val req = UpdateRequest("wrongold", "abc123", "abc123", Student("S001", "Still", "CS"))
      Post("/user/update", req.asJson).withHeaders(RawHeader("Authorization", token)) ~> routes ~> check {
        status shouldBe StatusCodes.Unauthorized
        responseAs[String] shouldBe "Old password incorrect"
      }
    }

    "fail to update with mismatched new passwords" in {
      val req = UpdateRequest("123456", "a", "b", Student("S001", "Still", "CS"))
      Post("/user/update", req.asJson).withHeaders(RawHeader("Authorization", token)) ~> routes ~> check {
        status shouldBe StatusCodes.BadRequest
        responseAs[String] shouldBe "New passwords do not match"
      }
    }

    "update password and info together" in {
      val req = UpdateRequest("123456", "newpass", "newpass", Student("S001", "Final Name", "Physics"))
      Post("/user/update", req.asJson).withHeaders(RawHeader("Authorization", token)) ~> routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[String] shouldBe "Info and password updated"
      }

      Get(s"/user/info?uid=$uid") ~> routes ~> check {
        status shouldBe StatusCodes.OK
        val json = responseAs[Json]
        val name = json.hcursor.downField("name").as[String].getOrElse("")
        val dept = json.hcursor.downField("department").as[String].getOrElse("")
        name shouldBe "Final Name"
        dept shouldBe "Physics"
      }
    }

    "fail to update with bad token" in {
      val req = UpdateRequest("", "", "", Student("S001", "Again", "Math"))
      Post("/user/update", req.asJson).withHeaders(RawHeader("Authorization", "bad.token.value")) ~> routes ~> check {
        status shouldBe StatusCodes.Unauthorized
        responseAs[String] shouldBe "Invalid token"
      }
    }

    "get uid from token using /user/uid" in {
      Get("/user/uid").withHeaders(RawHeader("Authorization", token)) ~> routes ~> check {
        status shouldBe StatusCodes.OK
        val json = responseAs[Json]
        val receivedUid = json.hcursor.downField("uid").as[String].getOrElse("")
        receivedUid shouldBe uid
      }
    }

    "fail to get uid with invalid token" in {
      Get("/user/uid").withHeaders(RawHeader("Authorization", "bad.token")) ~> routes ~> check {
        status shouldBe StatusCodes.Unauthorized
        responseAs[String] shouldBe "Invalid token"
      }
    }

    "get user info without token" in {
      Get(s"/user/info?uid=$uid") ~> routes ~> check {
        status shouldBe StatusCodes.OK
        val json = responseAs[Json]
        val name = json.hcursor.downField("name").as[String].getOrElse("")
        name shouldBe "Final Name"
      }
    }

    "fail to get info for nonexistent uid" in {
      Get("/user/info?uid=nonexistent") ~> routes ~> check {
        status shouldBe StatusCodes.NotFound
        responseAs[String] shouldBe "User not found"
      }
    }
  }
}
