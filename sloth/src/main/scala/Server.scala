package sloth.server

import sloth.core._
import sloth.internal.RouterMacro

import cats.Functor
import cats.syntax.functor._

class Server[PickleType, Result[_]](implicit
  private[sloth] val functor: Functor[Result]
) {

  def route[T](value: T): Router[PickleType, Result] = macro RouterMacro.impl[T, PickleType, Result]
}

object Server {
  def apply[PickleType, Result[_] : Functor](): Server[PickleType, Result] = new Server[PickleType, Result]
}

sealed trait ServerResult[+Result[_], PickleType] {
  def toEither: Either[ServerFailure, Result[PickleType]]
}
object ServerResult {
  case class Value[PickleType](raw: Any, serialized: PickleType)

  case class Failure[PickleType](arguments: List[List[Any]], failure: ServerFailure) extends ServerResult[Lambda[X => Nothing], PickleType] {
    def toEither = Left(failure)
  }
  case class Success[PickleType, Result[_] : Functor](arguments: List[List[Any]], result: Result[Value[PickleType]]) extends ServerResult[Result, PickleType] {
    def toEither = Right(result.map(_.serialized))
  }
}

trait Router[PickleType, Result[_]] { router =>
  def apply(request: Request[PickleType]): ServerResult[Result, PickleType]
  def orElse(otherRouter: Router[PickleType, Result]) = new Router[PickleType, Result] {
    def apply(request: Request[PickleType]): ServerResult[Result, PickleType] = router(request) match {
      case ServerResult.Failure(_, ServerFailure.PathNotFound(_)) => otherRouter(request)
      case other => other
    }
  }
}
