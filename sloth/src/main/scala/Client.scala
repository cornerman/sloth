package sloth

import cats.{MonadError, ~>}
import sloth.internal.TraitMacro

//TODO: move implicits to wire method
class Client[PickleType, Result[_]](
  private[sloth] val transport: RequestTransport[PickleType, Result],
  private[sloth] val logger: LogHandler
)(implicit
  private[sloth] val monadFailure: MonadClientFailure[Result]
) {

  def wire[T]: T = macro TraitMacro.impl[T, PickleType, Result]
}

object Client {
  def apply[PickleType, Result[_], ErrorType](transport: RequestTransport[PickleType, Result], logger: LogHandler = LogHandler.empty)(implicit monad: MonadClientFailure[Result]): Client[PickleType, Result] = new Client(transport, logger)(monad)
}

trait RequestTransport[PickleType, Result[_]] { transport =>
  def apply(request: Request[PickleType]): Result[PickleType]

  def mapK[R[_]](f: Result ~> R): RequestTransport[PickleType, R] = new RequestTransport[PickleType, R] {
    def apply(request: Request[PickleType]): R[PickleType] = f(transport(request))
  }
}
object RequestTransport {
  def apply[PickleType, Result[_]](f: Request[PickleType] => Result[PickleType]) = new RequestTransport[PickleType, Result] {
    def apply(request: Request[PickleType]): Result[PickleType] = f(request)
  }
}

trait LogHandler {
  def logRequest[Result[_], T](path: List[String], argumentObject: Product, result: Result[T])(implicit monad: MonadError[Result, _]): Result[T]
}
object LogHandler {
  def empty: LogHandler = new LogHandler {
    def logRequest[Result[_], T](path: List[String], argumentObject: Product, result: Result[T])(implicit monad: MonadError[Result, _]): Result[T] = result
  }
}
