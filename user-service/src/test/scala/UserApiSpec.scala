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
import pdi.jwt.{JwtCirce, JwtAlgorithm}
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
        responseAs[String] should include("Registered")
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
        responseAs[String] should include("Username exists")
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
        responseAs[String] should include("Passwords do not match")
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
        responseAs[String] should include("Invalid credentials")
      }
    }

    "update user info with token" in {
      val updateReq = UpdateRequest(
        None,
        None,
        None,
        Some(Student("S001", "Updated Name", "AI"))
      )
      Post("/user/update", updateReq.asJson).withHeaders(RawHeader("Authorization", token)) ~> routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[String] should include("Info updated")
      }
    }

    "change password successfully" in {
      val updateReq = UpdateRequest(
        Some("123456"),
        Some("newpass"),
        Some("newpass"),
        None
      )
      Post("/user/update", updateReq.asJson).withHeaders(RawHeader("Authorization", token)) ~> routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[String] should include("Updated with password")
      }
    }

    "fail to update with bad token" in {
      val req = UpdateRequest(None, None, None, Some(Student("S001", "Again", "Math")))
      Post("/user/update", req.asJson).withHeaders(RawHeader("Authorization", "bad.token.value")) ~> routes ~> check {
        status shouldBe StatusCodes.Unauthorized
        responseAs[String] should include("Invalid token")
      }
    }

    "fail to update password with wrong old password" in {
      val req = UpdateRequest(
        Some("wrongold"),
        Some("123123"),
        Some("123123"),
        None
      )
      Post("/user/update", req.asJson).withHeaders(RawHeader("Authorization", token)) ~> routes ~> check {
        status shouldBe StatusCodes.Unauthorized
        responseAs[String] should include("Old password incorrect")
      }
    }

    "fail to update with mismatched new passwords" in {
      val req = UpdateRequest(
        Some("newpass"),
        Some("a"),
        Some("b"),
        None
      )
      Post("/user/update", req.asJson).withHeaders(RawHeader("Authorization", token)) ~> routes ~> check {
        status shouldBe StatusCodes.BadRequest
        responseAs[String] should include("New passwords do not match")
      }
    }

    "fail to update with no data" in {
      val req = UpdateRequest(None, None, None, None)
      Post("/user/update", req.asJson).withHeaders(RawHeader("Authorization", token)) ~> routes ~> check {
        status shouldBe StatusCodes.BadRequest
        responseAs[String] should include("Nothing to update")
      }
    }

    "get user info with valid token" in {
      Get(s"/user/info?uid=$uid").withHeaders(RawHeader("Authorization", token)) ~> routes ~> check {
        status shouldBe StatusCodes.OK
        val info = responseAs[UserInfo]
        info match {
          case Student(_, name, _) => name shouldBe "Updated Name"
          case _ => fail("Expected Student info")
        }
      }
    }

    "fail to get user info with invalid token" in {
      Get(s"/user/info?uid=$uid").withHeaders(RawHeader("Authorization", "bad.token")) ~> routes ~> check {
        status shouldBe StatusCodes.Unauthorized
        responseAs[String] should include("Invalid token")
      }
    }

    "fail to get user info for nonexistent uid" in {
      Get("/user/info?uid=nonexistent").withHeaders(RawHeader("Authorization", token)) ~> routes ~> check {
        status shouldBe StatusCodes.NotFound
        responseAs[String] should include("User not found")
      }
    }
  }
}
