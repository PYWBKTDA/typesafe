package course

import cats.effect._
import cats.implicits._
import java.util.UUID

class CourseService(repo: CourseRepo, userClient: UserClient) {

  def createCourse(token: String, req: CreateCourseRequest): IO[Either[AppError, String]] =
    userClient.getUserInfo(token).flatMap {
      case Some((uid, "teacher", name)) =>
        repo.findByTeacherAndName(uid, req.name).flatMap {
          case true  => IO.pure(Left(ValidationError("Course already exists")))
          case false =>
            val id = UUID.randomUUID().toString
            val course = Course(id, req.name, uid, name, req.time, req.location)
            repo.insert(course).as(Right(id))
        }
      case Some((_, role, _)) => IO.pure(Left(AuthError(s"$role not allowed to create course")))
      case None => IO.pure(Left(AuthError("Invalid token")))
    }

  def updateCourse(token: String, req: UpdateCourseRequest): IO[Either[AppError, Boolean]] =
    userClient.getUserInfo(token).flatMap {
      case Some((uid, "teacher", _)) =>
        repo.get(req.courseId).flatMap {
          case None => IO.pure(Left(NotFoundError("Course not found")))
          case Some(course) =>
            if (course.uid != uid)
              IO.pure(Left(AuthError("Not owner")))
            else
              repo.update(uid, req).map(Right(_))
        }
      case Some(_) => IO.pure(Left(AuthError("Only teacher allowed")))
      case None    => IO.pure(Left(AuthError("Invalid token")))
    }

  def deleteCourse(token: String, req: DeleteCourseRequest): IO[Either[AppError, Boolean]] =
    userClient.getUserInfo(token).flatMap {
      case Some((uid, "teacher", _)) =>
        repo.get(req.courseId).flatMap {
          case None => IO.pure(Left(NotFoundError("Course not found")))
          case Some(course) =>
            if (course.uid != uid)
              IO.pure(Left(AuthError("Not owner")))
            else
              repo.delete(uid, req.courseId).map(Right(_))
        }
      case Some(_) => IO.pure(Left(AuthError("Only teacher allowed")))
      case None    => IO.pure(Left(AuthError("Invalid token")))
    }

  def selectCourse(token: String, req: EnrollmentRequest): IO[Either[AppError, Boolean]] =
    userClient.getUserInfo(token).flatMap {
      case Some((uid, "student", _)) =>
        repo.enroll(uid, req.courseId)
      case Some(_) => IO.pure(Left(AuthError("Only student can enroll")))
      case None    => IO.pure(Left(AuthError("Invalid token")))
    }

  def dropCourse(token: String, req: DropRequest): IO[Either[AppError, Boolean]] =
    userClient.getUserInfo(token).flatMap {
      case Some((uid, "student", _)) =>
        repo.get(req.courseId).flatMap {
          case None => IO.pure(Left(NotFoundError("Course not found")))
          case Some(_) => repo.drop(uid, req.courseId)
        }
      case Some(_) => IO.pure(Left(AuthError("Only student can drop")))
      case None    => IO.pure(Left(AuthError("Invalid token")))
    }

  def listAllCourses: IO[List[Course]] = repo.list()

  def getCourseInfo(courseId: String): IO[Either[AppError, Course]] =
    repo.get(courseId).map {
      case Some(c) => Right(c)
      case None    => Left(NotFoundError("Course not found"))
    }

  def checkEnrollment(uid: String, courseId: String): IO[Either[AppError, String]] =
    userClient.getUserInfoByUid(uid).flatMap {
      case Some((role, _)) =>
        repo.checkEnrollment(uid, courseId, role)
      case None =>
        IO.pure(Left(NotFoundError("User not found")))
    }

  def listStudents(courseId: String, token: String): IO[Either[AppError, List[String]]] =
    userClient.getUserInfo(token).flatMap {
      case Some((uid, "teacher", _)) =>
        repo.get(courseId).flatMap {
          case None => IO.pure(Left(NotFoundError("Course not found")))
          case Some(_) =>
            repo.own(uid, courseId).flatMap {
              case true  => repo.listStudents(courseId).map(Right(_))
              case false => IO.pure(Left(AuthError("Not owner")))
            }
        }
      case Some(_) => IO.pure(Left(AuthError("Only teacher allowed")))
      case None    => IO.pure(Left(AuthError("Invalid token")))
    }

  def myCourses(token: String): IO[Either[AppError, List[Course]]] =
    userClient.getUserInfo(token).flatMap {
      case Some((uid, "teacher", _)) => repo.findByTeacher(uid).map(Right(_))
      case Some((uid, "student", _)) => repo.findByStudent(uid).map(Right(_))
      case Some((_, other, _)) => IO.pure(Left(AuthError(s"Invalid role: $other")))
      case None => IO.pure(Left(AuthError("Invalid token")))
    }
}
