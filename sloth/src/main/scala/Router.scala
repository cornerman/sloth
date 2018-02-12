package sloth

import sloth.internal.RouterMacro

import cats.Functor
import cats.syntax.functor._

trait Router[PickleType, Result[_]] { router =>
  def apply(request: Request[PickleType]): RouterResult[PickleType, Result]

  def route[T](value: T)(implicit functor: Functor[Result]): Router[PickleType, Result] = macro RouterMacro.impl[T, PickleType, Result]

  final def orElse(otherRouter: Router[PickleType, Result]) = new Router[PickleType, Result] {
    def apply(request: Request[PickleType]): RouterResult[PickleType, Result] = router(request) match {
      case RouterResult.Failure(_, ServerFailure.PathNotFound(_)) => otherRouter(request)
      case other => other
    }
  }

  final def map[R[_]](f: RouterResult[PickleType, Result] => RouterResult[PickleType, R]): Router[PickleType, R] = new Router[PickleType, R] {
    def apply(request: Request[PickleType]): RouterResult[PickleType, R] = f(router(request))
  }
}
object Router {
  def apply[PickleType, Result[_]] = new Router[PickleType, Result] {
    def apply(request: Request[PickleType]): RouterResult[PickleType, Result] = RouterResult.Failure(Nil, ServerFailure.PathNotFound(request.path))
  }
}

sealed trait RouterResult[PickleType, +Result[_]] {
  def toEither: Either[ServerFailure, Result[PickleType]]
}
object RouterResult {
  case class Value[PickleType](raw: Any, serialized: PickleType)

  case class Failure[PickleType](arguments: List[List[Any]], failure: ServerFailure) extends RouterResult[PickleType, Lambda[X => Nothing]] {
    def toEither = Left(failure)
  }
  case class Success[PickleType, Result[_] : Functor](arguments: List[List[Any]], result: Result[Value[PickleType]]) extends RouterResult[PickleType, Result] {
    def toEither = Right(result.map(_.serialized))
  }
}
