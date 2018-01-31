package sloth.server

import sloth.core._
import sloth.internal.RouterMacro

import cats.Functor

trait Router[PickleType, Result[_]] { router =>
  def apply(request: Request[PickleType]): Server.ResultT[Result, PickleType]
  def orElse(otherRouter: Router[PickleType, Result]) = new Router[PickleType, Result] {
    def apply(request: Request[PickleType]): Server.ResultT[Result, PickleType] = router(request) match {
      case Left(ServerFailure.PathNotFound(_)) => otherRouter(request)
      case other => other
    }
  }
}

class Server[PickleType, Result[_]](
  private[sloth] val logger: LogHandler[Server.ResultT[Result, ?]]
)(implicit
  private[sloth] val functor: Functor[Result]
) {

  def route[T](value: T): Router[PickleType, Result] = macro RouterMacro.impl[T, PickleType, Result]
}

object Server {
  type ResultT[Result[_], T] = Either[ServerFailure, Result[T]]

  def apply[PickleType, Result[_] : Functor]: Server[PickleType, Result] = apply[PickleType, Result](LogHandler.empty[ResultT[Result, ?]])
  def apply[PickleType, Result[_] : Functor](logger: LogHandler[ResultT[Result, ?]]): Server[PickleType, Result] = new Server[PickleType, Result](logger)
}
