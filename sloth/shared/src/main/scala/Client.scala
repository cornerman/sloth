package sloth.client

import sloth.core._
import sloth.internal.TraitMacro

import cats.MonadError

trait RequestTransport[PickleType, Result[_]] {
  def apply(request: Request[PickleType]): Result[PickleType]
}
object RequestTransport {
  def apply[PickleType, Result[_]](f: Request[PickleType] => Result[PickleType]) =
    new RequestTransport[PickleType, Result] {
      def apply(request: Request[PickleType]): Result[PickleType] = f(request)
    }
}

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
