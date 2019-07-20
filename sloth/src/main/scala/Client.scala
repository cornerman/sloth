package sloth

import sloth.internal.TraitMacro

import cats.MonadError

//TODO: move implicits to wire method
class Client[PickleType, Result[_], ErrorType](
  private[sloth] val transport: RequestTransport[PickleType, Result],
  private[sloth] val logger: LogHandler[Result]
)(implicit
  private[sloth] val monad: MonadError[Result, _ >: ErrorType],
  private[sloth] val failureConverter: ClientFailureConvert[ErrorType]
) {

  def wire[T]: T = macro TraitMacro.impl[T, PickleType, Result, ErrorType]
}

object Client {
  def apply[PickleType, Result[_], ErrorType : ClientFailureConvert](transport: RequestTransport[PickleType, Result], logger: LogHandler[Result] = LogHandler.empty[Result])(implicit monad: MonadError[Result, _ >: ErrorType]) = new Client[PickleType, Result, ErrorType](transport, logger)
}

trait RequestTransport[PickleType, Result[_]] { transport =>
  def apply(request: Request[PickleType]): Result[PickleType]

  final def map[R[_]](f: Result[PickleType] => R[PickleType]): RequestTransport[PickleType, R] = new RequestTransport[PickleType, R] {
    def apply(request: Request[PickleType]): R[PickleType] = f(transport(request))
  }
}
object RequestTransport {
  def apply[PickleType, Result[_]](f: Request[PickleType] => Result[PickleType]) = new RequestTransport[PickleType, Result] {
    def apply(request: Request[PickleType]): Result[PickleType] = f(request)
  }
}

trait LogHandler[Result[_]] {
  def logRequest[T](path: List[String], argumentObject: Any, result: Result[T]): Result[T]
}
object LogHandler {
  def empty[Result[_]]: LogHandler[Result] = new LogHandler[Result] {
    def logRequest[T](path: List[String], argumentObject: Any, result: Result[T]): Result[T] = result
  }
}
