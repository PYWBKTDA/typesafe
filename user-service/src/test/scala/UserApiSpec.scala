package user

import cats.effect._
import com.typesafe.config.ConfigFactory
import org.http4s._
import org.http4s.implicits._
import org.http4s.circe._
import munit.CatsEffectSuite
import io.circe.syntax._
import io.circe.Json
import io.circe.generic.auto._
import user.Codecs._
import org.typelevel.ci._
import java.util.Properties
import doobie.Transactor

class UserApiSpec extends CatsEffectSuite {

  private val cfg = ConfigFactory.load().getConfig("db")
  private val props = new Properties()
  props.setProperty("user", cfg.getString("user"))
  props.setProperty("password", cfg.getString("password"))

  val transactor: Resource[IO, Transactor[IO]] =
    Resource.pure(
      Transactor.fromDriverManager[IO](
        cfg.getString("driver"),
        cfg.getString("url"),
        props,
        None
      )
    )

  val password = "123456"
  val tokenService = new JwtUtil

  def withApp(testFn: HttpApp[IO] => IO[Unit]): IO[Unit] =
    transactor.use { xa =>
      val repo = new UserRepo(xa)
      val service = new UserService(repo, tokenService)
      val routes = new UserRoutes(service).httpApp
      testFn(routes)
    }

  def extract(json: Json, key: String): String =
    json.hcursor.get[String](key).getOrElse("")

  def registerAndLogin(username: String)(routes: HttpApp[IO]): IO[(String, String)] = {
    val register = RegisterRequest(username, password, password, Student("S001", "User", "CS"))
    val login = LoginRequest(username, password)
    val registerReq = Request[IO](Method.POST, uri"/user/register").withEntity(register.asJson)
    val loginReq = Request[IO](Method.POST, uri"/user/login").withEntity(login.asJson)

    for {
      _ <- routes.run(registerReq)
      loginResp <- routes.run(loginReq)
      json <- loginResp.as[Json]
      Right(token) = json.hcursor.downField("data").downField("token").as[String]
      Right(uid) = tokenService.verify(token)
    } yield (token, uid)
  }

  test("register successfully") {
    val username = s"user_${System.currentTimeMillis()}"
    val req = RegisterRequest(username, password, password, Student("S001", "User", "CS"))
    val request = Request[IO](Method.POST, uri"/user/register").withEntity(req.asJson)
    withApp { routes =>
      for {
        resp <- routes.run(request)
        json <- resp.as[Json]
        _ = assertEquals(resp.status, Status.Ok)
        _ = assertEquals(extract(json, "message"), "Registered")
      } yield ()
    }
  }

  test("fail to register with duplicate username") {
    val username = s"user_${System.currentTimeMillis()}"
    val req = RegisterRequest(username, password, password, Student("S001", "User", "CS"))
    val request = Request[IO](Method.POST, uri"/user/register").withEntity(req.asJson)
    withApp { routes =>
      for {
        _ <- routes.run(request)
        resp <- routes.run(request)
        json <- resp.as[Json]
        _ = assertEquals(resp.status, Status.BadRequest)
        _ = assertEquals(extract(json, "message"), "Username exists")
      } yield ()
    }
  }

  test("fail to register with mismatched passwords") {
    val req = RegisterRequest("user2", "abc", "xyz", Student("S002", "Mismatch", "Math"))
    val request = Request[IO](Method.POST, uri"/user/register").withEntity(req.asJson)
    withApp { routes =>
      for {
        resp <- routes.run(request)
        json <- resp.as[Json]
        _ = assertEquals(resp.status, Status.BadRequest)
        _ = assertEquals(extract(json, "message"), "Passwords do not match")
      } yield ()
    }
  }

  test("fail to register with empty username or password") {
    val req = RegisterRequest("", "", "", Student("S002", "Empty", "Math"))
    val request = Request[IO](Method.POST, uri"/user/register").withEntity(req.asJson)
    withApp { routes =>
      for {
        resp <- routes.run(request)
        json <- resp.as[Json]
        _ = assertEquals(resp.status, Status.BadRequest)
        _ = assertEquals(extract(json, "message"), "Username or password cannot be empty")
      } yield ()
    }
  }

  test("login successfully and get token") {
    val username = s"user_${System.currentTimeMillis()}"
    withApp { routes =>
      registerAndLogin(username)(routes).map { case (token, uid) =>
        assert(token.nonEmpty)
        assert(uid.nonEmpty)
      }
    }
  }

  test("fail to login with non-existent username") {
    val req = LoginRequest("no_user", "password")
    val request = Request[IO](Method.POST, uri"/user/login").withEntity(req.asJson)
    withApp { routes =>
      for {
        resp <- routes.run(request)
        json <- resp.as[Json]
        _ = assertEquals(resp.status, Status.Unauthorized)
        _ = assertEquals(extract(json, "message"), "Invalid credentials")
      } yield ()
    }
  }

  test("fail to login with wrong password") {
    val username = s"user_${System.currentTimeMillis()}"
    val reg = RegisterRequest(username, password, password, Student("S001", "WrongPass", "CS"))
    val login = LoginRequest(username, "wrongpass")
    val regReq = Request[IO](Method.POST, uri"/user/register").withEntity(reg.asJson)
    val loginReq = Request[IO](Method.POST, uri"/user/login").withEntity(login.asJson)
    withApp { routes =>
      for {
        _ <- routes.run(regReq)
        resp <- routes.run(loginReq)
        json <- resp.as[Json]
        _ = assertEquals(resp.status, Status.Unauthorized)
        _ = assertEquals(extract(json, "message"), "Invalid credentials")
      } yield ()
    }
  }

  test("fail to login with empty username and password") {
    val req = LoginRequest("", "")
    val request = Request[IO](Method.POST, uri"/user/login").withEntity(req.asJson)
    withApp { routes =>
      for {
        resp <- routes.run(request)
        json <- resp.as[Json]
        _ = assertEquals(resp.status, Status.BadRequest)
        _ = assertEquals(extract(json, "message"), "Username or password cannot be empty")
      } yield ()
    }
  }

  test("update user info only") {
    val username = s"user_${System.currentTimeMillis()}"
    withApp { routes =>
      registerAndLogin(username)(routes).flatMap { case (token, uid) =>
        val update = UpdateRequest(None, None, None, Student("S001", "Updated", "AI"))
        val updateReq = Request[IO](Method.POST, uri"/user/update").withEntity(update.asJson)
          .putHeaders(Header.Raw(ci"Authorization", s"Bearer $token"))
        for {
          resp <- routes.run(updateReq)
          json <- resp.as[Json]
          _ = assertEquals(resp.status, Status.Ok)
          _ = assertEquals(extract(json, "message"), "Info updated")
        } yield ()
      }
    }
  }

  test("update user info and password") {
    val username = s"user_${System.currentTimeMillis()}"
    withApp { routes =>
      registerAndLogin(username)(routes).flatMap { case (token, _) =>
        val update = UpdateRequest(Some(password), Some("newpass"), Some("newpass"), Student("S001", "X", "X"))
        val req = Request[IO](Method.POST, uri"/user/update").withEntity(update.asJson)
          .putHeaders(Header.Raw(ci"Authorization", s"Bearer $token"))
        for {
          resp <- routes.run(req)
          json <- resp.as[Json]
          _ = assertEquals(resp.status, Status.Ok)
          _ = assertEquals(extract(json, "message"), "Info and password updated")
        } yield ()
      }
    }
  }

  test("fail to update: wrong old password") {
    val username = s"user_${System.currentTimeMillis()}"
    withApp { routes =>
      registerAndLogin(username)(routes).flatMap { case (token, _) =>
        val update = UpdateRequest(Some("wrong"), Some("x"), Some("x"), Student("S", "X", "X"))
        val req = Request[IO](Method.POST, uri"/user/update").withEntity(update.asJson)
          .putHeaders(Header.Raw(ci"Authorization", s"Bearer $token"))
        for {
          resp <- routes.run(req)
          json <- resp.as[Json]
          _ = assertEquals(resp.status, Status.Unauthorized)
          _ = assertEquals(extract(json, "message"), "Old password incorrect")
        } yield ()
      }
    }
  }

  test("fail to update: new password mismatch") {
    val username = s"user_${System.currentTimeMillis()}"
    withApp { routes =>
      registerAndLogin(username)(routes).flatMap { case (token, _) =>
        val update = UpdateRequest(Some(password), Some("a"), Some("b"), Student("S", "X", "X"))
        val req = Request[IO](Method.POST, uri"/user/update").withEntity(update.asJson)
          .putHeaders(Header.Raw(ci"Authorization", s"Bearer $token"))
        for {
          resp <- routes.run(req)
          json <- resp.as[Json]
          _ = assertEquals(resp.status, Status.BadRequest)
          _ = assertEquals(extract(json, "message"), "New passwords do not match")
        } yield ()
      }
    }
  }

  test("fail to update: incomplete password fields") {
    val username = s"user_${System.currentTimeMillis()}"
    withApp { routes =>
      registerAndLogin(username)(routes).flatMap { case (token, _) =>
        val update = UpdateRequest(Some(password), Some("newpass"), None, Student("S", "X", "X"))
        val req = Request[IO](Method.POST, uri"/user/update").withEntity(update.asJson)
          .putHeaders(Header.Raw(ci"Authorization", s"Bearer $token"))
        for {
          resp <- routes.run(req)
          json <- resp.as[Json]
          _ = assertEquals(resp.status, Status.BadRequest)
          _ = assertEquals(extract(json, "message"), "Incomplete password fields")
        } yield ()
      }
    }
  }

  test("fail to update: no token") {
    val update = UpdateRequest(None, None, None, Student("S", "X", "X"))
    val req = Request[IO](Method.POST, uri"/user/update").withEntity(update.asJson)
    withApp { routes =>
      for {
        resp <- routes.run(req)
        json <- resp.as[Json]
        _ = assertEquals(resp.status, Status.Unauthorized)
        _ = assertEquals(extract(json, "message"), "No token")
      } yield ()
    }
  }

  test("fail to update: invalid token") {
    val update = UpdateRequest(None, None, None, Student("S", "X", "X"))
    val req = Request[IO](Method.POST, uri"/user/update").withEntity(update.asJson)
      .putHeaders(Header.Raw(ci"Authorization", "Bearer invalid"))
    withApp { routes =>
      for {
        resp <- routes.run(req)
        json <- resp.as[Json]
        _ = assertEquals(resp.status, Status.Unauthorized)
        _ = assertEquals(extract(json, "message"), "Invalid token")
      } yield ()
    }
  }

  test("get uid from token successfully") {
    val username = s"user_${System.currentTimeMillis()}"
    withApp { routes =>
      registerAndLogin(username)(routes).flatMap { case (token, uid) =>
        val req = Request[IO](Method.GET, uri"/user/uid").putHeaders(Header.Raw(ci"Authorization", s"Bearer $token"))
        for {
          resp <- routes.run(req)
          json <- resp.as[Json]
          _ = assertEquals(resp.status, Status.Ok)
          _ = assertEquals(extract(json.hcursor.downField("data").focus.get, "uid"), uid)
        } yield ()
      }
    }
  }

  test("fail to get uid: no token") {
    val req = Request[IO](Method.GET, uri"/user/uid")
    withApp { routes =>
      for {
        resp <- routes.run(req)
        json <- resp.as[Json]
        _ = assertEquals(resp.status, Status.Unauthorized)
        _ = assertEquals(extract(json, "message"), "No token")
      } yield ()
    }
  }

  test("fail to get uid: invalid token") {
    val req = Request[IO](Method.GET, uri"/user/uid").putHeaders(Header.Raw(ci"Authorization", "Bearer xyz"))
    withApp { routes =>
      for {
        resp <- routes.run(req)
        json <- resp.as[Json]
        _ = assertEquals(resp.status, Status.Unauthorized)
        _ = assertEquals(extract(json, "message"), "Invalid token")
      } yield ()
    }
  }

  test("get user info successfully") {
    val username = s"user_${System.currentTimeMillis()}"
    withApp { routes =>
      registerAndLogin(username)(routes).flatMap { case (_, uid) =>
        val req = Request[IO](Method.GET, uri"/user/info".withQueryParam("uid", uid))
        for {
          resp <- routes.run(req)
          json <- resp.as[Json]
          cursor = json.hcursor.downField("data")
          _ = assertEquals(resp.status, Status.Ok)
          _ = assertEquals(cursor.get[String]("name").getOrElse(""), "User")
        } yield ()
      }
    }
  }

  test("fail to get user info for nonexistent uid") {
    val req = Request[IO](Method.GET, uri"/user/info".withQueryParam("uid", "not_exist"))
    withApp { routes =>
      for {
        resp <- routes.run(req)
        json <- resp.as[Json]
        _ = assertEquals(resp.status, Status.NotFound)
        _ = assertEquals(extract(json, "message"), "User not found")
      } yield ()
    }
  }
}
