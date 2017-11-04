package sloth.client

import sloth.core._
import sloth.internal.TraitMacro

import cats.MonadError

class Client[PickleType, Result[_], ErrorType](
  private[sloth] val transport: RequestTransport[PickleType, Result])(implicit
  private[sloth] val monad: MonadError[Result, _ >: ErrorType],
  private[sloth] val failureIsError: SlothFailure => ErrorType) {

  def wire[T]: T = macro TraitMacro.impl[T, PickleType, Result]
}

object Client {
  def apply[PickleType, Result[_]](
    transport: RequestTransport[PickleType, Result])(implicit
    monad: MonadError[Result, _ >: SlothFailure]) = new Client[PickleType, Result, SlothFailure](transport)

  def apply[PickleType, Result[_], ErrorType](
    transport: RequestTransport[PickleType, Result])(implicit
    monad: MonadError[Result, _ >: ErrorType],
    failureIsError: SlothFailure => ErrorType) = new Client[PickleType, Result, ErrorType](transport)
}
