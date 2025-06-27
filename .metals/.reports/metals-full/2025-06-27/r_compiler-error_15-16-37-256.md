file:///C:/Users/19567/Desktop/project/backend/src/main/scala/example/main.scala
### java.lang.AssertionError: NoDenotation.owner

occurred in the presentation compiler.

presentation compiler configuration:


action parameters:
uri: file:///C:/Users/19567/Desktop/project/backend/src/main/scala/example/main.scala
text:
```scala
case class PasswordChangeRequest(username: String, oldPassword: String, newPassword: String)

trait AuthService[F[_]] {
  def register(req: AuthRequest): F[Either[ApiError, Unit]]
  def login(req: AuthRequest): F[Either[ApiError, User]]
  def allUsers(): F[List[User]]
  def changePassword(req: PasswordChangeRequest): F[Either[ApiError, Unit]]
}

class AuthServiceImpl(repo: UserRepo[IO]) extends AuthService[IO] {
  def register(req: AuthRequest) = repo.create(req.username, BCrypt.hashpw(req.password, BCrypt.gensalt()))
  def login(req: AuthRequest) = repo.find(req.username).map {
    case None => Left(UsernameNotFoundError)
    case Some((u, hash)) =>
      if (BCrypt.checkpw(req.password, hash)) Right(u)
      else Left(PasswordIncorrectError)
  }
  def allUsers() = repo.list()
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
}

trait UserRepo[F[_]] {
  def create(username: String, passwordHash: String): F[Either[ApiError, Unit]]
  def find(username: String): F[Option[(User, String)]]
  def list(): F[List[User]]
  def updatePassword(username: String, newHash: String): F[Boolean]
}

class DoobieUserRepo(xa: Transactor[IO]) extends UserRepo[IO] {
  def create(u: String, p: String) = ...
  def find(u: String) = ...
  def list() = ...
  def updatePassword(u: String, p: String): IO[Boolean] =
    sql"UPDATE users SET password = $p WHERE username = $u".update.run.transact(xa).map(_ > 0)
}

private implicit val pwdDec: EntityDecoder[IO, PasswordChangeRequest] = jsonOf

private def routes(svc: AuthService[IO]): HttpRoutes[IO] = HttpRoutes.of[IO] {
  case req @ POST -> Root / "register" =>
    req.as[AuthRequest].flatMap(svc.register).flatMap {
      case Right(_)  => Created("注册成功")
      case Left(err) => IO.pure(Response[IO](err.status).withEntity(err.message))
    }
  case req @ POST -> Root / "login" =>
    req.as[AuthRequest].flatMap(svc.login).flatMap {
      case Right(u)  => Ok(Json.obj("user" -> u.asJson))
      case Left(err) => IO.pure(Response[IO](err.status).withEntity(err.message))
    }
  case GET -> Root / "users" =>
    svc.allUsers().flatMap(us => Ok(us.asJson))
  case req @ POST -> Root / "change-password" =>
    req.as[PasswordChangeRequest].flatMap(svc.changePassword).flatMap {
      case Right(_)  => Ok("修改成功")
      case Left(err) => IO.pure(Response[IO](err.status).withEntity(err.message))
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