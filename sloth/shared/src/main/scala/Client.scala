package sloth.client

import sloth.core._
import sloth.internal.TraitMacro

import cats.MonadError

class Client[PickleType, Result[_], ErrorType](
  private[sloth] val transport: RequestTransport[PickleType, Result]
)(implicit
  private[sloth] val monad: MonadError[Result, _ >: ErrorType],
  private[sloth] val failureIsError: SlothClientFailure => ErrorType
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
