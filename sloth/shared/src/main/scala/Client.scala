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
  private[sloth] val transport: RequestTransport[PickleType, Result]
)(implicit
  private[sloth] val monad: MonadError[Result, _ >: ErrorType],
  private[sloth] val failureIsError: SlothClientFailure => ErrorType //TODO typeclass for failure
) {

  def wire[T]: T = macro TraitMacro.impl[T, PickleType, Result, ErrorType]
}

object Client {
  import SlothClientFailure.SlothException

  def apply[PickleType, Result[_]](
    transport: RequestTransport[PickleType, Result]
  )(implicit
    monad: MonadError[Result, _ >: SlothException]
  ) = apply[PickleType, Result, SlothException](transport)

  def apply[PickleType, Result[_], ErrorType](
    transport: RequestTransport[PickleType, Result]
  )(implicit
    monad: MonadError[Result, _ >: ErrorType],
    failureIsError: SlothClientFailure => ErrorType
  ) = new Client[PickleType, Result, ErrorType](transport)
}
