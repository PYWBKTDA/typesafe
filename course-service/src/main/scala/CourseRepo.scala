package course

import cats.effect._
import cats.syntax.applicative._
import cats.syntax.either._
import cats.syntax.apply._
import doobie._
import doobie.implicits._

class CourseRepo(xa: Transactor[IO]) {

  def insert(course: Course): IO[Int] =
    sql"""
      INSERT INTO courses (id, name, uid, teacher_name, time, location)
      VALUES (${course.id}, ${course.name}, ${course.uid}, ${course.teacherName}, ${course.time}, ${course.location})
    """.update.run.transact(xa)

  def findByTeacherAndName(uid: String, name: String): IO[Boolean] =
    sql"SELECT EXISTS (SELECT 1 FROM courses WHERE uid = $uid AND name = $name)"
      .query[Boolean].unique.transact(xa)

  def update(uid: String, req: UpdateCourseRequest): IO[Boolean] =
    sql"""
      UPDATE courses SET name = ${req.name}, time = ${req.time}, location = ${req.location}
      WHERE id = ${req.courseId} AND uid = $uid
    """.update.run.map(_ > 0).transact(xa)

  def delete(uid: String, courseId: String): IO[Boolean] =
    sql"DELETE FROM courses WHERE id = $courseId AND uid = $uid"
      .update.run.map(_ > 0).transact(xa)

  def enroll(uid: String, courseId: String): IO[Either[AppError, Boolean]] = {
    val exists = sql"SELECT EXISTS (SELECT 1 FROM courses WHERE id = $courseId)".query[Boolean].unique
    val selected = sql"SELECT EXISTS (SELECT 1 FROM enrollments WHERE uid = $uid AND course_id = $courseId)".query[Boolean].unique
    val insert = sql"INSERT INTO enrollments (uid, course_id) VALUES ($uid, $courseId)".update.run.map(_ > 0)

    val logic: ConnectionIO[Either[AppError, Boolean]] = (exists, selected).mapN {
      case (false, _)      => Left(NotFoundError("Course not found"))
      case (true, true)    => Left(ValidationError("Already enrolled"))
      case (true, false)   => Right(())
    }.flatMap {
      case Left(e) =>
        val res: Either[AppError, Boolean] = Left(e match {
          case e: ValidationError => e
          case e: NotFoundError   => e
          case _                  => ValidationError(e.toString)
        })
        res.pure[ConnectionIO]

      case Right(_) =>
        insert.map(b => Right(b): Either[AppError, Boolean])
    }

    logic.transact(xa)
  }

  def drop(uid: String, courseId: String): IO[Either[AppError, Boolean]] =
    sql"DELETE FROM enrollments WHERE uid = $uid AND course_id = $courseId"
      .update.run.map {
        case 0 => Left(NotFoundError("Not enrolled"))
        case _ => Right(true)
      }.transact(xa)

  def list(): IO[List[Course]] =
    sql"SELECT id, name, uid, teacher_name, time, location FROM courses"
      .query[Course].to[List].transact(xa)

  def get(id: String): IO[Option[Course]] =
    sql"SELECT id, name, uid, teacher_name, time, location FROM courses WHERE id = $id"
      .query[Course].option.transact(xa)

  def checkEnrollment(uid: String, courseId: String, role: String): IO[Either[AppError, String]] = {
    val courseExists = sql"SELECT EXISTS (SELECT 1 FROM courses WHERE id = $courseId)".query[Boolean].unique
    val studentSelected = sql"SELECT EXISTS (SELECT 1 FROM enrollments WHERE uid = $uid AND course_id = $courseId)".query[Boolean].unique
    val teacherCreated = sql"SELECT EXISTS (SELECT 1 FROM courses WHERE id = $courseId AND uid = $uid)".query[Boolean].unique

    (courseExists, studentSelected, teacherCreated).mapN {
      case (false, _, _) => Left(NotFoundError("Course not found"))
      case (true, true, _) if role == "student" => Right("Selected")
      case (true, false, _) if role == "student" => Right("Not selected")
      case (true, _, true) if role == "teacher" => Right("Created")
      case (true, _, false) if role == "teacher" => Right("Not created")
      case _ => Right("Unknown")
    }.transact(xa)
  }

  def own(uid: String, courseId: String): IO[Boolean] =
    sql"SELECT EXISTS (SELECT 1 FROM courses WHERE id = $courseId AND uid = $uid)"
      .query[Boolean].unique.transact(xa)

  def listStudents(courseId: String): IO[List[String]] =
    sql"SELECT uid FROM enrollments WHERE course_id = $courseId"
      .query[String].to[List].transact(xa)

  def findByTeacher(uid: String): IO[List[Course]] =
    sql"SELECT id, name, uid, teacher_name, time, location FROM courses WHERE uid = $uid"
      .query[Course].to[List].transact(xa)

  def findByStudent(uid: String): IO[List[Course]] =
    sql"""
      SELECT c.id, c.name, c.uid, c.teacher_name, c.time, c.location
      FROM courses c
      JOIN enrollments e ON c.id = e.course_id
      WHERE e.uid = $uid
    """.query[Course].to[List].transact(xa)
}
