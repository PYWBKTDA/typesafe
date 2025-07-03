package user

import cats.effect._
import cats.implicits._
import java.util.UUID
import com.github.t3hnar.bcrypt._

class UserService(repo: UserRepo, tokenService: TokenService) {

  def register(req: RegisterRequest): IO[Either[AppError, String]] =
    if (req.username.trim.isEmpty || req.password.trim.isEmpty)
      IO.pure(Left(ValidationError("Username or password cannot be empty")))
    else if (req.password != req.confirmPassword)
      IO.pure(Left(ValidationError("Passwords do not match")))
    else
      repo.findByUsername(req.username).flatMap {
        case Some(_) => IO.pure(Left(ValidationError("Username exists")))
        case None =>
          val uid = UUID.randomUUID().toString
          val hashed = req.password.bcrypt
          val user = User(uid, req.username, hashed, req.info)
          repo.insert(user).as(Right("Registered"))
      }

  def login(req: LoginRequest): IO[Either[AppError, String]] =
    if (req.username.trim.isEmpty || req.password.trim.isEmpty)
      IO.pure(Left(ValidationError("Username or password cannot be empty")))
    else
      repo.findByUsername(req.username).map {
        case Some(user) if req.password.isBcrypted(user.password) =>
          Right(tokenService.generate(user.uid))
        case _ => Left(AuthError("Invalid credentials"))
      }

  def update(token: String, req: UpdateRequest): IO[Either[AppError, String]] =
    tokenService.verify(token) match {
      case Left(err) => IO.pure(Left(err))
      case Right(uid) =>
        repo.findByUid(uid).flatMap {
          case None => IO.pure(Left(NotFoundError("User not found")))
          case Some(user) =>
            val updated = for {
              newPwd <- validatePasswordUpdate(req, user.password)
            } yield user.copy(password = newPwd.getOrElse(user.password), info = req.info)

            updated match {
              case Left(err) => IO.pure(Left(err))
              case Right(newUser) =>
                repo.update(newUser).as {
                  val msg = if (req.oldPassword.exists(_.nonEmpty)) "Info and password updated" else "Info updated"
                  Right(msg)
                }
            }
        }
    }

  private def validatePasswordUpdate(req: UpdateRequest, currentHash: String): Either[AppError, Option[String]] =
    (req.oldPassword, req.newPassword, req.confirmPassword) match {
      case (None | Some(""), None | Some(""), None | Some("")) => Right(None)
      case (Some(oldPwd), Some(newPwd), Some(confirm)) =>
        if (!oldPwd.isBcrypted(currentHash)) Left(AuthError("Old password incorrect"))
        else if (newPwd != confirm) Left(ValidationError("New passwords do not match"))
        else Right(Some(newPwd.bcrypt))
      case _ => Left(ValidationError("Incomplete password fields"))
    }

  def getUserInfo(uid: String): IO[Either[AppError, UserInfo]] =
    repo.findByUid(uid).map(_.map(_.info).toRight(NotFoundError("User not found")))

  def getUidFromToken(token: String): IO[Either[AppError, String]] =
    IO.pure(tokenService.verify(token))
}
