package sloth.client

import cats.MonadError
import sloth.core._
import sloth.internal.TraitMacro

class Client[Encoder[_], Decoder[_], PickleType, Result[_], ErrorType] private(
  private[sloth] val transport: RequestTransport[PickleType, Result])(implicit
  private[sloth] val serializer: Serializer[Encoder, Decoder, PickleType],
  private[sloth] val monad: MonadError[Result, _ >: ErrorType],
  private[sloth] val failureIsError: SlothFailure => ErrorType) {

  def wire[T]: T = macro TraitMacro.impl[T, PickleType, Result]
}

object Client {
  def apply[Encoder[_], Decoder[_], PickleType, Result[_]](
    transport: RequestTransport[PickleType, Result])(implicit
    serializer: Serializer[Encoder, Decoder, PickleType],
    monad: MonadError[Result, _ >: SlothFailure]) = new Client[Encoder, Decoder, PickleType, Result, SlothFailure](transport)

  def apply[Encoder[_], Decoder[_], PickleType, Result[_], ErrorType](
    transport: RequestTransport[PickleType, Result])(implicit
    serializer: Serializer[Encoder, Decoder, PickleType],
    monad: MonadError[Result, _ >: ErrorType],
    failureIsError: SlothFailure => ErrorType) = new Client[Encoder, Decoder, PickleType, Result, ErrorType](transport)
}
