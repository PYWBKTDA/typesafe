package course

import cats.effect._
import cats.effect.unsafe.implicits.global
import munit.CatsEffectSuite
import com.typesafe.config.ConfigFactory
import doobie.hikari.HikariTransactor
import java.util.UUID

class CourseApiSpec extends CatsEffectSuite {

  val config = ConfigFactory.load().getConfig("db")

  val transactor: Resource[IO, HikariTransactor[IO]] =
    HikariTransactor.newHikariTransactor[IO](
      config.getString("driver"),
      config.getString("url"),
      config.getString("user"),
      config.getString("password"),
      scala.concurrent.ExecutionContext.global
    )

  test("POST /course/create - 创建成功") {
    transactor.use { xa =>
      val repo = new CourseRepo(xa)
      val userClient = new UserClient {
        override def getUserInfo(token: String): IO[Option[(String, String, String)]] =
          IO.pure(Some(("teacher-uid", "teacher", "王老师")))
        override def getUserInfoByUid(uid: String): IO[Option[(String, String)]] =
          IO.pure(Some(("teacher", "王老师")))
      }
      val service = new CourseService(repo, userClient)
      val req = CreateCourseRequest("Scala课_" + System.currentTimeMillis(), "周五10:00", "教室A1")
      service.createCourse("Bearer valid-token", req).map {
        case Right(_) => assert(true)
        case Left(err) => fail(err.msg)
      }
    }
  }

  test("POST /course/create - 课程已存在") {
    transactor.use { xa =>
      val repo = new CourseRepo(xa)
      val courseName = "重复课程"
      val userClient = new UserClient {
        override def getUserInfo(token: String): IO[Option[(String, String, String)]] =
          IO.pure(Some(("teacher-uid", "teacher", "王老师")))
        override def getUserInfoByUid(uid: String): IO[Option[(String, String)]] =
          IO.pure(Some(("teacher", "王老师")))
      }
      val service = new CourseService(repo, userClient)
      val req = CreateCourseRequest(courseName, "时间", "地点")
      val token = "Bearer valid-token"
      for {
        _ <- service.createCourse(token, req)
        result <- service.createCourse(token, req)
        _ = result match {
          case Left(ValidationError(msg)) => assertEquals(msg, "Course already exists")
          case _ => fail("应该返回课程已存在错误")
        }
      } yield ()
    }
  }

  test("POST /course/create - 非教师身份") {
    transactor.use { xa =>
      val repo = new CourseRepo(xa)
      val userClient = new UserClient {
        override def getUserInfo(token: String): IO[Option[(String, String, String)]] =
          IO.pure(Some(("student-uid", "student", "学生A")))
        override def getUserInfoByUid(uid: String): IO[Option[(String, String)]] =
          IO.pure(Some(("student", "学生A")))
      }
      val service = new CourseService(repo, userClient)
      val req = CreateCourseRequest("学生尝试创建", "时间", "地点")
      service.createCourse("Bearer student-token", req).map {
        case Left(AuthError(msg)) => assertEquals(msg, "student not allowed to create course")
        case _ => fail("应拒绝学生创建课程")
      }
    }
  }

  test("POST /course/create - 缺少 token") {
    transactor.use { xa =>
      val repo = new CourseRepo(xa)
      val userClient = new UserClient {
        override def getUserInfo(token: String): IO[Option[(String, String, String)]] = IO.pure(None)
        override def getUserInfoByUid(uid: String): IO[Option[(String, String)]] = IO.pure(None)
      }
      val service = new CourseService(repo, userClient)
      val req = CreateCourseRequest("无token", "时间", "地点")
      service.createCourse("", req).map {
        case Left(AuthError(msg)) => assertEquals(msg, "Invalid token")
        case _ => fail("应返回 Invalid token")
      }
    }
  }

  test("POST /course/create - token 无效") {
    transactor.use { xa =>
      val repo = new CourseRepo(xa)
      val userClient = new UserClient {
        override def getUserInfo(token: String): IO[Option[(String, String, String)]] = IO.pure(None)
        override def getUserInfoByUid(uid: String): IO[Option[(String, String)]] = IO.pure(None)
      }
      val service = new CourseService(repo, userClient)
      val req = CreateCourseRequest("无效token", "时间", "地点")
      service.createCourse("Bearer xxxx", req).map {
        case Left(AuthError(msg)) => assertEquals(msg, "Invalid token")
        case _ => fail("应返回 Invalid token")
      }
    }
  }

  test("POST /course/update - 更新成功") {
    transactor.use { xa =>
      val repo = new CourseRepo(xa)
      val userClient = new UserClient {
        override def getUserInfo(token: String): IO[Option[(String, String, String)]] =
          IO.pure(Some(("teacher-uid", "teacher", "王老师")))
        override def getUserInfoByUid(uid: String): IO[Option[(String, String)]] =
          IO.pure(Some(("teacher", "王老师")))
      }
      val service = new CourseService(repo, userClient)
      val id = UUID.randomUUID().toString
      val course = Course(id, "原课程", "teacher-uid", "王老师", "时间1", "地点1")
      for {
        _ <- repo.insert(course)
        result <- service.updateCourse("Bearer token", UpdateCourseRequest(id, "新课程", "时间2", "地点2"))
        _ = assertEquals(result, Right(true))
      } yield ()
    }
  }

  test("POST /course/update - 非教师身份") {
    transactor.use { xa =>
      val repo = new CourseRepo(xa)
      val userClient = new UserClient {
        override def getUserInfo(token: String): IO[Option[(String, String, String)]] =
          IO.pure(Some(("student-uid", "student", "学生A")))
        override def getUserInfoByUid(uid: String): IO[Option[(String, String)]] =
          IO.pure(Some(("student", "学生A")))
      }
      val service = new CourseService(repo, userClient)
      val id = UUID.randomUUID().toString
      for {
        result <- service.updateCourse("Bearer token", UpdateCourseRequest(id, "修改", "时间", "地点"))
        _ = assertEquals(result, Left(AuthError("Only teacher allowed")))
      } yield ()
    }
  }

  test("POST /course/update - 非课程创建者") {
    transactor.use { xa =>
      val repo = new CourseRepo(xa)
      val id = UUID.randomUUID().toString
      val course = Course(id, "非本人课程", "other-teacher", "其他老师", "时间", "地点")
      val userClient = new UserClient {
        override def getUserInfo(token: String): IO[Option[(String, String, String)]] =
          IO.pure(Some(("teacher-uid", "teacher", "王老师")))
        override def getUserInfoByUid(uid: String): IO[Option[(String, String)]] =
          IO.pure(Some(("teacher", "王老师")))
      }
      val service = new CourseService(repo, userClient)
      for {
        _ <- repo.insert(course)
        result <- service.updateCourse("Bearer token", UpdateCourseRequest(id, "改不了", "时间", "地点"))
        _ = assertEquals(result, Left(AuthError("Not owner")))
      } yield ()
    }
  }

  test("POST /course/update - 课程不存在") {
    transactor.use { xa =>
      val repo = new CourseRepo(xa)
      val userClient = new UserClient {
        override def getUserInfo(token: String): IO[Option[(String, String, String)]] =
          IO.pure(Some(("teacher-uid", "teacher", "王老师")))
        override def getUserInfoByUid(uid: String): IO[Option[(String, String)]] =
          IO.pure(Some(("teacher", "王老师")))
      }
      val service = new CourseService(repo, userClient)
      val id = UUID.randomUUID().toString
      for {
        result <- service.updateCourse("Bearer token", UpdateCourseRequest(id, "不存在", "时间", "地点"))
        _ = assertEquals(result, Left(NotFoundError("Course not found")))
      } yield ()
    }
  }

  test("POST /course/update - 缺少 token") {
    transactor.use { xa =>
      val repo = new CourseRepo(xa)
      val userClient = new UserClient {
        override def getUserInfo(token: String): IO[Option[(String, String, String)]] = IO.pure(None)
        override def getUserInfoByUid(uid: String): IO[Option[(String, String)]] = IO.pure(None)
      }
      val service = new CourseService(repo, userClient)
      val id = UUID.randomUUID().toString
      val req = UpdateCourseRequest(id, "标题", "时间", "地点")
      service.updateCourse("", req).map {
        case Left(AuthError(msg)) => assertEquals(msg, "Invalid token")
        case _ => fail("应返回 Invalid token")
      }
    }
  }

  test("POST /course/update - token 无效") {
    transactor.use { xa =>
      val repo = new CourseRepo(xa)
      val userClient = new UserClient {
        override def getUserInfo(token: String): IO[Option[(String, String, String)]] = IO.pure(None)
        override def getUserInfoByUid(uid: String): IO[Option[(String, String)]] = IO.pure(None)
      }
      val service = new CourseService(repo, userClient)
      val id = UUID.randomUUID().toString
      val req = UpdateCourseRequest(id, "标题", "时间", "地点")
      service.updateCourse("Bearer fake", req).map {
        case Left(AuthError(msg)) => assertEquals(msg, "Invalid token")
        case _ => fail("应返回 Invalid token")
      }
    }
  }

  test("POST /course/delete - 删除成功") {
    transactor.use { xa =>
      val repo = new CourseRepo(xa)
      val id = UUID.randomUUID().toString
      val course = Course(id, "课程", "teacher-uid", "王老师", "时间", "地点")
      val userClient = new UserClient {
        override def getUserInfo(token: String) = IO.pure(Some(("teacher-uid", "teacher", "王老师")))
        override def getUserInfoByUid(uid: String) = IO.pure(Some(("teacher", "王老师")))
      }
      val service = new CourseService(repo, userClient)
      for {
        _ <- repo.insert(course)
        result <- service.deleteCourse("Bearer token", DeleteCourseRequest(id))
        _ = assertEquals(result, Right(true))
      } yield ()
    }
  }

  test("POST /course/delete - 非教师身份") {
    transactor.use { xa =>
      val repo = new CourseRepo(xa)
      val id = UUID.randomUUID().toString
      val userClient = new UserClient {
        override def getUserInfo(token: String) = IO.pure(Some(("student-uid", "student", "学生甲")))
        override def getUserInfoByUid(uid: String) = IO.pure(Some(("student", "学生甲")))
      }
      val service = new CourseService(repo, userClient)
      service.deleteCourse("Bearer token", DeleteCourseRequest(id)).map {
        case Left(AuthError(msg)) => assertEquals(msg, "Only teacher allowed")
        case _ => fail("应拒绝学生删除课程")
      }
    }
  }

  test("POST /course/delete - 非课程创建者") {
    transactor.use { xa =>
      val repo = new CourseRepo(xa)
      val id = UUID.randomUUID().toString
      val course = Course(id, "别人的课", "other-teacher", "别人", "时间", "地点")
      val userClient = new UserClient {
        override def getUserInfo(token: String) = IO.pure(Some(("teacher-uid", "teacher", "王老师")))
        override def getUserInfoByUid(uid: String) = IO.pure(Some(("teacher", "王老师")))
      }
      val service = new CourseService(repo, userClient)
      for {
        _ <- repo.insert(course)
        result <- service.deleteCourse("Bearer token", DeleteCourseRequest(id))
        _ = assertEquals(result, Left(AuthError("Not owner")))
      } yield ()
    }
  }

  test("POST /course/delete - 课程不存在") {
    transactor.use { xa =>
      val repo = new CourseRepo(xa)
      val id = UUID.randomUUID().toString
      val userClient = new UserClient {
        override def getUserInfo(token: String) = IO.pure(Some(("teacher-uid", "teacher", "王老师")))
        override def getUserInfoByUid(uid: String) = IO.pure(Some(("teacher", "王老师")))
      }
      val service = new CourseService(repo, userClient)
      service.deleteCourse("Bearer token", DeleteCourseRequest(id)).map {
        case Left(NotFoundError(msg)) => assertEquals(msg, "Course not found")
        case _ => fail("应返回课程不存在")
      }
    }
  }

  test("POST /course/delete - 缺少 token") {
    transactor.use { xa =>
      val repo = new CourseRepo(xa)
      val id = UUID.randomUUID().toString
      val userClient = new UserClient {
        override def getUserInfo(token: String) = IO.pure(None)
        override def getUserInfoByUid(uid: String) = IO.pure(None)
      }
      val service = new CourseService(repo, userClient)
      service.deleteCourse("", DeleteCourseRequest(id)).map {
        case Left(AuthError(msg)) => assertEquals(msg, "Invalid token")
        case _ => fail("应返回 Invalid token")
      }
    }
  }

  test("POST /course/delete - token 无效") {
    transactor.use { xa =>
      val repo = new CourseRepo(xa)
      val id = UUID.randomUUID().toString
      val userClient = new UserClient {
        override def getUserInfo(token: String) = IO.pure(None)
        override def getUserInfoByUid(uid: String) = IO.pure(None)
      }
      val service = new CourseService(repo, userClient)
      service.deleteCourse("Bearer fake", DeleteCourseRequest(id)).map {
        case Left(AuthError(msg)) => assertEquals(msg, "Invalid token")
        case _ => fail("应返回 Invalid token")
      }
    }
  }
  
  test("POST /course/select - 选课成功") {
    transactor.use { xa =>
      val repo = new CourseRepo(xa)
      val uid = "student-uid"
      val cid = UUID.randomUUID().toString
      val userClient = new UserClient {
        override def getUserInfo(token: String) = IO.pure(Some((uid, "student", "学生A")))
        override def getUserInfoByUid(uid: String) = IO.pure(Some(("student", "学生A")))
      }
      val service = new CourseService(repo, userClient)
      for {
        _ <- repo.insert(Course(cid, "课程", "teacher-uid", "老师", "时间", "地点"))
        result <- service.selectCourse("Bearer token", EnrollmentRequest(cid))
        _ = assertEquals(result, Right(true))
      } yield ()
    }
  }

  test("POST /course/select - 非学生身份") {
    transactor.use { xa =>
      val repo = new CourseRepo(xa)
      val courseId = UUID.randomUUID().toString
      val userClient = new UserClient {
        override def getUserInfo(token: String) = IO.pure(Some(("teacher-uid", "teacher", "王老师")))
        override def getUserInfoByUid(uid: String) = IO.pure(Some(("teacher", "王老师")))
      }
      val service = new CourseService(repo, userClient)
      for {
        _ <- repo.insert(Course(courseId, "课程", "teacher-uid", "王老师", "时间", "地点"))
        result <- service.selectCourse("Bearer token", EnrollmentRequest(courseId))
        _ = assertEquals(result, Left(AuthError("Only student can enroll")))
      } yield ()
    }
  }

  test("POST /course/select - 已选过该课程") {
    transactor.use { xa =>
      val repo = new CourseRepo(xa)
      val uid = "student-uid"
      val cid = UUID.randomUUID().toString
      val userClient = new UserClient {
        override def getUserInfo(token: String) = IO.pure(Some((uid, "student", "学生A")))
        override def getUserInfoByUid(uid: String) = IO.pure(Some(("student", "学生A")))
      }
      val service = new CourseService(repo, userClient)
      for {
        _ <- repo.insert(Course(cid, "课程", "teacher-uid", "老师", "时间", "地点"))
        _ <- repo.enroll(uid, cid)
        result <- service.selectCourse("Bearer token", EnrollmentRequest(cid))
        _ = assertEquals(result, Left(ValidationError("Already enrolled")))
      } yield ()
    }
  }

  test("POST /course/select - 课程不存在") {
    transactor.use { xa =>
      val repo = new CourseRepo(xa)
      val courseId = UUID.randomUUID().toString
      val userClient = new UserClient {
        override def getUserInfo(token: String) = IO.pure(Some(("student-uid", "student", "学生A")))
        override def getUserInfoByUid(uid: String) = IO.pure(Some(("student", "学生A")))
      }
      val service = new CourseService(repo, userClient)
      service.selectCourse("Bearer token", EnrollmentRequest(courseId)).map {
        case Left(NotFoundError(msg)) => assertEquals(msg, "Course not found")
        case _ => fail("应返回 Course not found")
      }
    }
  }

  test("POST /course/select - 缺少 token") {
    transactor.use { xa =>
      val repo = new CourseRepo(xa)
      val courseId = UUID.randomUUID().toString
      val userClient = new UserClient {
        override def getUserInfo(token: String) = IO.pure(None)
        override def getUserInfoByUid(uid: String) = IO.pure(None)
      }
      val service = new CourseService(repo, userClient)
      service.selectCourse("", EnrollmentRequest(courseId)).map {
        case Left(AuthError(msg)) => assertEquals(msg, "Invalid token")
        case _ => fail("应返回 Invalid token")
      }
    }
  }

  test("POST /course/select - token 无效") {
    transactor.use { xa =>
      val repo = new CourseRepo(xa)
      val courseId = UUID.randomUUID().toString
      val userClient = new UserClient {
        override def getUserInfo(token: String) = IO.pure(None)
        override def getUserInfoByUid(uid: String) = IO.pure(None)
      }
      val service = new CourseService(repo, userClient)
      service.selectCourse("Bearer fake", EnrollmentRequest(courseId)).map {
        case Left(AuthError(msg)) => assertEquals(msg, "Invalid token")
        case _ => fail("应返回 Invalid token")
      }
    }
  }

  test("POST /course/drop - 退课成功") {
    transactor.use { xa =>
      val repo = new CourseRepo(xa)
      val uid = "student-uid"
      val cid = UUID.randomUUID().toString
      val userClient = new UserClient {
        override def getUserInfo(token: String) = IO.pure(Some((uid, "student", "学生A")))
        override def getUserInfoByUid(uid: String) = IO.pure(Some(("student", "学生A")))
      }
      val service = new CourseService(repo, userClient)
      for {
        _ <- repo.insert(Course(cid, "课程", "teacher-uid", "老师", "时间", "地点"))
        _ <- repo.enroll(uid, cid)
        result <- service.dropCourse("Bearer token", DropRequest(cid))
        _ = assertEquals(result, Right(true))
      } yield ()
    }
  }

  test("POST /course/drop - 非学生身份") {
    transactor.use { xa =>
      val repo = new CourseRepo(xa)
      val cid = UUID.randomUUID().toString
      val userClient = new UserClient {
        override def getUserInfo(token: String) = IO.pure(Some(("teacher-uid", "teacher", "老师")))
        override def getUserInfoByUid(uid: String) = IO.pure(Some(("teacher", "老师")))
      }
      val service = new CourseService(repo, userClient)
      service.dropCourse("Bearer token", DropRequest(cid)).map {
        case Left(AuthError(msg)) => assertEquals(msg, "Only student can drop")
        case _ => fail("应返回 Only student can drop")
      }
    }
  }

  test("POST /course/drop - 未选该课程") {
    transactor.use { xa =>
      val repo = new CourseRepo(xa)
      val uid = "student-uid"
      val cid = UUID.randomUUID().toString
      val userClient = new UserClient {
        override def getUserInfo(token: String) = IO.pure(Some((uid, "student", "学生A")))
        override def getUserInfoByUid(uid: String) = IO.pure(Some(("student", "学生A")))
      }
      val service = new CourseService(repo, userClient)
      for {
        _ <- repo.insert(Course(cid, "课程", "teacher-uid", "老师", "时间", "地点"))
        result <- service.dropCourse("Bearer token", DropRequest(cid))
        _ = assertEquals(result, Left(NotFoundError("Not enrolled")))
      } yield ()
    }
  }

  test("POST /course/drop - 课程不存在") {
    transactor.use { xa =>
      val repo = new CourseRepo(xa)
      val uid = "student-uid"
      val cid = UUID.randomUUID().toString
      val userClient = new UserClient {
        override def getUserInfo(token: String) = IO.pure(Some((uid, "student", "学生A")))
        override def getUserInfoByUid(uid: String) = IO.pure(Some(("student", "学生A")))
      }
      val service = new CourseService(repo, userClient)
      service.dropCourse("Bearer token", DropRequest(cid)).map {
        case Left(NotFoundError(msg)) => assertEquals(msg, "Course not found")
        case _ => fail("应返回 Course not found")
      }
    }
  }

  test("POST /course/drop - 缺少 token") {
    transactor.use { xa =>
      val repo = new CourseRepo(xa)
      val cid = UUID.randomUUID().toString
      val userClient = new UserClient {
        override def getUserInfo(token: String) = IO.pure(None)
        override def getUserInfoByUid(uid: String) = IO.pure(None)
      }
      val service = new CourseService(repo, userClient)
      service.dropCourse("", DropRequest(cid)).map {
        case Left(AuthError(msg)) => assertEquals(msg, "Invalid token")
        case _ => fail("应返回 Invalid token")
      }
    }
  }

  test("POST /course/drop - token 无效") {
    transactor.use { xa =>
      val repo = new CourseRepo(xa)
      val cid = UUID.randomUUID().toString
      val userClient = new UserClient {
        override def getUserInfo(token: String) = IO.pure(None)
        override def getUserInfoByUid(uid: String) = IO.pure(None)
      }
      val service = new CourseService(repo, userClient)
      service.dropCourse("Bearer fake", DropRequest(cid)).map {
        case Left(AuthError(msg)) => assertEquals(msg, "Invalid token")
        case _ => fail("应返回 Invalid token")
      }
    }
  }
  
  test("GET /course/check - 学生已选该课程") {
    transactor.use { xa =>
      val repo = new CourseRepo(xa)
      val uid = "student-uid"
      val cid = UUID.randomUUID().toString
      val userClient = new UserClient {
        override def getUserInfo(token: String) = IO.pure(None)
        override def getUserInfoByUid(uid: String) = IO.pure(Some(("student", "学生A")))
      }
      val service = new CourseService(repo, userClient)
      for {
        _ <- repo.insert(Course(cid, "课程", "teacher-uid", "老师", "时间", "地点"))
        _ <- repo.enroll(uid, cid)
        result <- service.checkEnrollment(uid, cid)
        _ = assertEquals(result, Right("Selected"))
      } yield ()
    }
  }

  test("GET /course/check - 教师创建了该课程") {
    transactor.use { xa =>
      val repo = new CourseRepo(xa)
      val uid = "teacher-uid"
      val cid = UUID.randomUUID().toString
      val userClient = new UserClient {
        override def getUserInfo(token: String) = IO.pure(None)
        override def getUserInfoByUid(uid: String) = IO.pure(Some(("teacher", "王老师")))
      }
      val service = new CourseService(repo, userClient)
      for {
        _ <- repo.insert(Course(cid, "课程", uid, "王老师", "时间", "地点"))
        result <- service.checkEnrollment(uid, cid)
        _ = assertEquals(result, Right("Created"))
      } yield ()
    }
  }

  test("GET /course/check - 学生未选该课程") {
    transactor.use { xa =>
      val repo = new CourseRepo(xa)
      val uid = "student-uid"
      val cid = UUID.randomUUID().toString
      val userClient = new UserClient {
        override def getUserInfo(token: String) = IO.pure(None)
        override def getUserInfoByUid(uid: String) = IO.pure(Some(("student", "学生A")))
      }
      val service = new CourseService(repo, userClient)
      for {
        _ <- repo.insert(Course(cid, "课程", "teacher-uid", "老师", "时间", "地点"))
        result <- service.checkEnrollment(uid, cid)
        _ = assertEquals(result, Right("Not selected"))
      } yield ()
    }
  }

  test("GET /course/check - 教师未创建该课程") {
    transactor.use { xa =>
      val repo = new CourseRepo(xa)
      val uid = "teacher-uid"
      val cid = UUID.randomUUID().toString
      val course = Course(cid, "课程", "other-teacher", "张老师", "时间", "地点")
      val userClient = new UserClient {
        override def getUserInfo(token: String) = IO.pure(None)
        override def getUserInfoByUid(uid: String) = IO.pure(Some(("teacher", "老师")))
      }
      val service = new CourseService(repo, userClient)
      for {
        _ <- repo.insert(course)
        result <- service.checkEnrollment(uid, cid)
      } yield assertEquals(result, Right("Not created"))
    }
  }

  test("GET /course/check - uid 不存在") {
    transactor.use { xa =>
      val repo = new CourseRepo(xa)
      val uid = "ghost"
      val cid = UUID.randomUUID().toString
      val userClient = new UserClient {
        override def getUserInfo(token: String) = IO.pure(None)
        override def getUserInfoByUid(uid: String) = IO.pure(None)
      }
      val service = new CourseService(repo, userClient)
      service.checkEnrollment(uid, cid).map {
        case Left(NotFoundError(msg)) => assertEquals(msg, "User not found")
        case _ => fail("应返回 User not found")
      }
    }
  }

  test("GET /course/check - courseId 不存在") {
    transactor.use { xa =>
      val repo = new CourseRepo(xa)
      val cid = UUID.randomUUID().toString
      val uid = "student-uid"
      val userClient = new UserClient {
        override def getUserInfo(token: String) = IO.pure(None)
        override def getUserInfoByUid(uid: String) = IO.pure(Some(("student", "学生A")))
      }
      val service = new CourseService(repo, userClient)
      service.checkEnrollment(uid, cid).map {
        case Left(NotFoundError(msg)) => assertEquals(msg, "Course not found")
        case _ => fail("应返回 Course not found")
      }
    }
  }
  
  test("GET /course/list - 查询成功") {
    transactor.use { xa =>
      val repo = new CourseRepo(xa)
      val userClient = new UserClient {
        override def getUserInfo(token: String) = IO.pure(None)
        override def getUserInfoByUid(uid: String) = IO.pure(None)
      }
      val service = new CourseService(repo, userClient)
      for {
        _ <- repo.insert(Course(UUID.randomUUID().toString, "课程1", "t1", "老师1", "时间", "地点"))
        _ <- repo.insert(Course(UUID.randomUUID().toString, "课程2", "t2", "老师2", "时间", "地点"))
        result <- service.listCourses()
        _ = assert(result.nonEmpty)
      } yield ()
    }
  }

  test("GET /course/info - 查询成功") {
    transactor.use { xa =>
      val repo = new CourseRepo(xa)
      val id = UUID.randomUUID().toString
      val course = Course(id, "课程", "t1", "老师1", "时间", "地点")
      val userClient = new UserClient {
        override def getUserInfo(token: String) = IO.pure(None)
        override def getUserInfoByUid(uid: String) = IO.pure(None)
      }
      val service = new CourseService(repo, userClient)
      for {
        _ <- repo.insert(course)
        result <- service.getCourseInfo(id)
        _ = assertEquals(result, Right(course))
      } yield ()
    }
  }

  test("GET /course/info - 课程不存在") {
    transactor.use { xa =>
      val repo = new CourseRepo(xa)
      val id = UUID.randomUUID().toString
      val userClient = new UserClient {
        override def getUserInfo(token: String) = IO.pure(None)
        override def getUserInfoByUid(uid: String) = IO.pure(None)
      }
      val service = new CourseService(repo, userClient)
      service.getCourseInfo(id).map {
        case Left(NotFoundError(msg)) => assertEquals(msg, "Course not found")
        case _ => fail("应返回 Course not found")
      }
    }
  }


}
