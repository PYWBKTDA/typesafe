file:///C:/Users/19567/Desktop/project/backend/src/main/scala/example/main.scala
### java.lang.AssertionError: NoDenotation.owner

occurred in the presentation compiler.

presentation compiler configuration:


action parameters:
uri: file:///C:/Users/19567/Desktop/project/backend/src/main/scala/example/main.scala
text:
```scala
package com.example

import cats.effect._
import cats.data.EitherT
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.circe._
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Router
import org.http4s.server.middleware.CORS
import io.circe._, io.circe.generic.auto._, io.circe.syntax._
import doobie._, doobie.hikari.HikariTransactor, doobie.implicits._
import scala.concurrent.ExecutionContext
import org.flywaydb.core.Flyway
import org.postgresql.ds.PGSimpleDataSource
import org.postgresql.util.PSQLState
import org.mindrot.jbcrypt.BCrypt
import com.comcast.ip4s.{Host, Port}

case class User(id:Int,username:String)
case class AuthRequest(username:String,password:String)
case class PasswordChangeRequest(username: String, oldPassword: String, newPassword: String)

sealed trait ApiError{def status:Status;def message:Json}
case object UserExistsError extends ApiError{val status=Status.Conflict;val message=Json.obj("error"->Json.fromString("用户已存在"))}
case object UsernameNotFoundError extends ApiError{val status=Status.Unauthorized;val message=Json.obj("error"->Json.fromString("用户名不存在"))}
case object PasswordIncorrectError extends ApiError{val status=Status.Unauthorized;val message=Json.obj("error"->Json.fromString("密码错误"))}
case object UnknownError extends ApiError{val status=Status.InternalServerError;val message=Json.obj("error"->Json.fromString("未知错误"))}

trait UserRepo[F[_]]{
  def create(username:String,passwordHash:String):F[Either[ApiError,Unit]]
  def find(username:String):F[Option[(User,String)]]
  def list():F[List[User]]
}

class DoobieUserRepo(xa:Transactor[IO]) extends UserRepo[IO]{
  def create(u:String,p:String):IO[Either[ApiError,Unit]]=
    sql"INSERT INTO users(username,password) VALUES($u,$p)".update.run.transact(xa).attempt.map{
      case Left(e:java.sql.SQLException) if e.getSQLState==PSQLState.UNIQUE_VIOLATION.getState=>Left(UserExistsError)
      case Left(_) => Left(UnknownError)
      case Right(_) => Right(())
    }
  def find(u:String):IO[Option[(User,String)]]=
    sql"SELECT id,username,password FROM users WHERE username=$u".query[(Int,String,String)].option.transact(xa).map(_.map{case(id,n,pw)=>(User(id,n),pw)})
  def list():IO[List[User]]=sql"SELECT id,username FROM users".query[User].to[List].transact(xa)
}

trait AuthService[F[_]]{
  def register(req:AuthRequest):F[Either[ApiError,Unit]]
  def login(req:AuthRequest):F[Either[ApiError,User]]
  def changePassword(req: PasswordChangeRequest): F[Either[ApiError, Unit]]
  def allUsers():F[List[User]]
}

class AuthServiceImpl(repo:UserRepo[IO]) extends AuthService[IO]{
  def register(req:AuthRequest):IO[Either[ApiError,Unit]]=repo.create(req.username,BCrypt.hashpw(req.password,BCrypt.gensalt()))
  def login(req:AuthRequest):IO[Either[ApiError,User]]=repo.find(req.username).map{
    case None => Left(UsernameNotFoundError)
    case Some((u, hash)) =>
      if (BCrypt.checkpw(req.password, hash)) Right(u)
      else Left(PasswordIncorrectError)
  }
  def changePassword(req: PasswordChangeRequest) =
  repo.find(req.username).flatMap {
    case None => IO.pure(Left(UsernameNotFoundError))
    case Some((user, hash)) =>
      if (!BCrypt.checkpw(req.oldPassword, hash)) IO.pure(Left(PasswordIncorrectError))
      else {
        val newHash = BCrypt.hashpw(req.newPassword, BCrypt.gensalt())
        repo.updatePassword(user.username, newHash).map {
          case true  => Right(())
          case false => Left(UnknownError)
        }
      }
  }
  def allUsers():IO[List[User]]=repo.list()
}

object Main extends IOApp.Simple{
  private val jdbcUrl="jdbc:postgresql://localhost:5432/db"
  private val dbUser="db"
  private val dbPass="root"
  def run:IO[Unit]=
    migrateDb*>
    HikariTransactor.newHikariTransactor[IO]("org.postgresql.Driver",jdbcUrl,dbUser,dbPass,ExecutionContext.global).use{xa=>
      val repo=new DoobieUserRepo(xa)
      val svc=new AuthServiceImpl(repo)
      val app=Router("/api"->routes(svc)).orNotFound
      EmberServerBuilder.default[IO]
        .withHost(Host.fromString("0.0.0.0").get)
        .withPort(Port.fromInt(9000).get)
        .withHttpApp(CORS.policy.withAllowOriginAll(app))
        .build
        .useForever
    }
  private implicit val dec:EntityDecoder[IO,AuthRequest]=jsonOf
  private def routes(svc:AuthService[IO]):HttpRoutes[IO]=HttpRoutes.of[IO]{
    case req@POST -> Root/"register" =>
      req.as[AuthRequest].flatMap(svc.register).flatMap{
        case Right(_)=>Created(Json.obj("status"->Json.fromString("注册成功")))
        case Left(err)=>IO.pure(Response[IO](err.status).withEntity(err.message))
      }
    case req@POST -> Root/"login" =>
      req.as[AuthRequest].flatMap(svc.login).flatMap{
        case Right(u)=>Ok(Json.obj("user"->u.asJson))
        case Left(err)=>IO.pure(Response[IO](err.status).withEntity(err.message))
      }
    case GET -> Root/"users" =>
      svc.allUsers().flatMap(us=>Ok(us.asJson))
  }
  private def migrateDb:IO[Unit]=IO.blocking{
    val ds=new PGSimpleDataSource()
    ds.setUrl(jdbcUrl);ds.setUser(dbUser);ds.setPassword(dbPass)
    Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate()
  }
}

```



#### Error stacktrace:

```
dotty.tools.dotc.core.SymDenotations$NoDenotation$.owner(SymDenotations.scala:2609)
	dotty.tools.dotc.core.SymDenotations$SymDenotation.isSelfSym(SymDenotations.scala:715)
	dotty.tools.dotc.semanticdb.ExtractSemanticDB$Extractor.traverse(ExtractSemanticDB.scala:330)
	dotty.tools.dotc.ast.Trees$Instance$TreeTraverser.apply(Trees.scala:1770)
	dotty.tools.dotc.ast.Trees$Instance$TreeTraverser.apply(Trees.scala:1770)
	dotty.tools.dotc.ast.Trees$Instance$TreeAccumulator.fold$1(Trees.scala:1636)
	dotty.tools.dotc.ast.Trees$Instance$TreeAccumulator.apply(Trees.scala:1638)
	dotty.tools.dotc.ast.Trees$Instance$TreeAccumulator.foldOver(Trees.scala:1669)
	dotty.tools.dotc.ast.Trees$Instance$TreeTraverser.traverseChildren(Trees.scala:1771)
	dotty.tools.dotc.semanticdb.ExtractSemanticDB$Extractor.traverse(ExtractSemanticDB.scala:457)
	dotty.tools.dotc.ast.Trees$Instance$TreeTraverser.apply(Trees.scala:1770)
	dotty.tools.dotc.ast.Trees$Instance$TreeTraverser.apply(Trees.scala:1770)
	dotty.tools.dotc.ast.Trees$Instance$TreeAccumulator.foldOver(Trees.scala:1671)
	dotty.tools.dotc.ast.Trees$Instance$TreeTraverser.traverseChildren(Trees.scala:1771)
	dotty.tools.dotc.semanticdb.ExtractSemanticDB$Extractor.traverse(ExtractSemanticDB.scala:457)
	dotty.tools.dotc.semanticdb.ExtractSemanticDB$Extractor.traverse(ExtractSemanticDB.scala:454)
	dotty.tools.dotc.ast.Trees$Instance$TreeTraverser.apply(Trees.scala:1770)
	dotty.tools.dotc.ast.Trees$Instance$TreeTraverser.apply(Trees.scala:1770)
	dotty.tools.dotc.ast.Trees$Instance$TreeAccumulator.foldOver(Trees.scala:1677)
	dotty.tools.dotc.ast.Trees$Instance$TreeTraverser.traverseChildren(Trees.scala:1771)
	dotty.tools.dotc.semanticdb.ExtractSemanticDB$Extractor.traverse(ExtractSemanticDB.scala:457)
	dotty.tools.dotc.ast.Trees$Instance$TreeTraverser.apply(Trees.scala:1770)
	dotty.tools.dotc.ast.Trees$Instance$TreeTraverser.apply(Trees.scala:1770)
	dotty.tools.dotc.ast.Trees$Instance$TreeAccumulator.fold$1(Trees.scala:1636)
	dotty.tools.dotc.ast.Trees$Instance$TreeAccumulator.apply(Trees.scala:1638)
	dotty.tools.dotc.ast.Trees$Instance$TreeAccumulator.foldOver(Trees.scala:1675)
	dotty.tools.dotc.ast.Trees$Instance$TreeTraverser.traverseChildren(Trees.scala:1771)
	dotty.tools.dotc.semanticdb.ExtractSemanticDB$Extractor.traverse(ExtractSemanticDB.scala:457)
	dotty.tools.dotc.semanticdb.ExtractSemanticDB$Extractor.traverse$$anonfun$13(ExtractSemanticDB.scala:391)
	scala.runtime.function.JProcedure1.apply(JProcedure1.java:15)
	scala.runtime.function.JProcedure1.apply(JProcedure1.java:10)
	scala.collection.immutable.List.foreach(List.scala:334)
	dotty.tools.dotc.semanticdb.ExtractSemanticDB$Extractor.traverse(ExtractSemanticDB.scala:386)
	dotty.tools.dotc.ast.Trees$Instance$TreeTraverser.apply(Trees.scala:1770)
	dotty.tools.dotc.ast.Trees$Instance$TreeTraverser.apply(Trees.scala:1770)
	dotty.tools.dotc.ast.Trees$Instance$TreeAccumulator.foldOver(Trees.scala:1724)
	dotty.tools.dotc.ast.Trees$Instance$TreeTraverser.traverseChildren(Trees.scala:1771)
	dotty.tools.dotc.semanticdb.ExtractSemanticDB$Extractor.traverse(ExtractSemanticDB.scala:354)
	dotty.tools.dotc.semanticdb.ExtractSemanticDB$Extractor.traverse$$anonfun$11(ExtractSemanticDB.scala:377)
	scala.runtime.function.JProcedure1.apply(JProcedure1.java:15)
	scala.runtime.function.JProcedure1.apply(JProcedure1.java:10)
	scala.collection.immutable.List.foreach(List.scala:334)
	dotty.tools.dotc.semanticdb.ExtractSemanticDB$Extractor.traverse(ExtractSemanticDB.scala:377)
	dotty.tools.dotc.ast.Trees$Instance$TreeTraverser.apply(Trees.scala:1770)
	dotty.tools.dotc.ast.Trees$Instance$TreeTraverser.apply(Trees.scala:1770)
	dotty.tools.dotc.ast.Trees$Instance$TreeAccumulator.foldOver(Trees.scala:1728)
	dotty.tools.dotc.ast.Trees$Instance$TreeAccumulator.foldOver(Trees.scala:1642)
	dotty.tools.dotc.ast.Trees$Instance$TreeTraverser.traverseChildren(Trees.scala:1771)
	dotty.tools.dotc.semanticdb.ExtractSemanticDB$Extractor.traverse(ExtractSemanticDB.scala:351)
	dotty.tools.dotc.semanticdb.ExtractSemanticDB$Extractor.traverse$$anonfun$1(ExtractSemanticDB.scala:315)
	scala.runtime.function.JProcedure1.apply(JProcedure1.java:15)
	scala.runtime.function.JProcedure1.apply(JProcedure1.java:10)
	scala.collection.immutable.List.foreach(List.scala:334)
	dotty.tools.dotc.semanticdb.ExtractSemanticDB$Extractor.traverse(ExtractSemanticDB.scala:315)
	dotty.tools.pc.SemanticdbTextDocumentProvider.textDocument(SemanticdbTextDocumentProvider.scala:36)
	dotty.tools.pc.ScalaPresentationCompiler.semanticdbTextDocument$$anonfun$1(ScalaPresentationCompiler.scala:242)
```
#### Short summary: 

java.lang.AssertionError: NoDenotation.owner