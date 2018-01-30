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

class Client[PickleType, Result[_], ErrorType](implicit
  private[sloth] val monad: MonadError[Result, _ >: ErrorType],
  private[sloth] val failureIsError: ClientFailure => ErrorType //TODO typeclass for failure
) {

  def wire[T](transport: RequestTransport[PickleType, Result]): T = macro TraitMacro.impl[T, PickleType, Result, ErrorType]
}

object Client {
  import ClientFailure.ClientException

  def apply[PickleType, Result[_]](implicit
    monad: MonadError[Result, _ >: ClientException]
  ) = apply[PickleType, Result, ClientException]

  def apply[PickleType, Result[_], ErrorType](implicit
    monad: MonadError[Result, _ >: ErrorType],
    failureIsError: ClientFailure => ErrorType
  ) = new Client[PickleType, Result, ErrorType]
}
