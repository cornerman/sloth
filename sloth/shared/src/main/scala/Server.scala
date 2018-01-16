package sloth.server

import sloth.core._
import sloth.internal.RouterMacro

import cats.Functor

trait Router[PickleType, Result[_]] { router =>
  def apply(request: Request[PickleType]): Either[ServerFailure, Result[PickleType]]
  def orElse(otherRouter: Router[PickleType, Result]) = new Router[PickleType, Result] {
    def apply(request: Request[PickleType]): Either[ServerFailure, Result[PickleType]] = router(request) match {
      case Left(ServerFailure.PathNotFound(_)) => otherRouter(request)
      case other => other
    }
  }
}

class Server[PickleType, Result[_]](implicit private[sloth] val functor: Functor[Result]) {

  def route[T](value: T)(implicit pathMapper: PathMapper): Router[PickleType, Result] = macro RouterMacro.impl[T, PickleType, Result]
}

object Server {
  def apply[PickleType, Result[_]](implicit functor: Functor[Result]) = new Server[PickleType, Result]
}
