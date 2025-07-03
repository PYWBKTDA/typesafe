package user

import doobie._
import doobie.implicits._
import cats.effect._
import io.circe.syntax._
import io.circe.parser._
import user.Codecs._

class UserRepo(xa: Transactor[IO]) {
  private def decodeUser(t: (String, String, String, String)): Option[User] = {
    val (uid, username, password, infoStr) = t
    for {
      json <- parse(infoStr).toOption
      info <- json.as[UserInfo].toOption
    } yield User(uid, username, password, info)
  }

  def findByUsername(username: String): IO[Option[User]] =
    sql"SELECT uid, username, password, info FROM users WHERE username = $username"
      .query[(String, String, String, String)]
      .map(decodeUser)
      .option
      .map(_.flatten)
      .transact(xa)

  def findByUid(uid: String): IO[Option[User]] =
    sql"SELECT uid, username, password, info FROM users WHERE uid = $uid"
      .query[(String, String, String, String)]
      .map(decodeUser)
      .option
      .map(_.flatten)
      .transact(xa)

  def insert(user: User): IO[Int] = {
    val json = user.info.asJson.noSpaces
    sql"INSERT INTO users (uid, username, password, info) VALUES (${user.uid}, ${user.username}, ${user.password}, $json)"
      .update.run
      .transact(xa)
  }

  def update(user: User): IO[Int] = {
    val json = user.info.asJson.noSpaces
    sql"UPDATE users SET password = ${user.password}, info = $json WHERE uid = ${user.uid}"
      .update.run
      .transact(xa)
  }
}
