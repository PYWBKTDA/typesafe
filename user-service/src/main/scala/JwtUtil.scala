package user

import pdi.jwt.{JwtCirce, JwtClaim, JwtAlgorithm}
import com.typesafe.config.ConfigFactory

trait TokenService {
  def generate(uid: String): String
  def verify(token: String): Either[AppError, String]
}

class JwtUtil extends TokenService {
  private val key = ConfigFactory.load().getString("jwt.secret")

  def generate(uid: String): String =
    JwtCirce.encode(JwtClaim(subject = Some(uid)), key, JwtAlgorithm.HS256)

  def verify(token: String): Either[AppError, String] =
    JwtCirce.decode(token, key, Seq(JwtAlgorithm.HS256)).toEither
      .flatMap(claim => claim.subject.toRight(AuthError("Invalid token")))
      .left.map(_ => AuthError("Invalid token"))
}
