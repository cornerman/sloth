package sloth.client

import sloth.core._
import sloth.internal.TraitMacro

import cats.MonadError

class Client[Encoder[_], Decoder[_], PickleType, Result[_], ErrorType](
  private[sloth] val transport: RequestTransport[PickleType, Result])(implicit
  private[sloth] val serializer: Serializer[Encoder, Decoder, PickleType],
  private[sloth] val monad: MonadError[Result, _ >: ErrorType],
  private[sloth] val failureIsError: SlothFailure => ErrorType) {

  def wire[T]: T = macro TraitMacro.impl[T, PickleType, Result]
}

class ClientFactory[Encoder[_], Decoder[_], PickleType] {
  def apply[Result[_]](
    transport: RequestTransport[PickleType, Result])(implicit
    serializer: Serializer[Encoder, Decoder, PickleType],
    monad: MonadError[Result, _ >: SlothFailure]) = new Client[Encoder, Decoder, PickleType, Result, SlothFailure](transport)

  def apply[Result[_], ErrorType](
    transport: RequestTransport[PickleType, Result])(implicit
    serializer: Serializer[Encoder, Decoder, PickleType],
    monad: MonadError[Result, _ >: ErrorType],
    failureIsError: SlothFailure => ErrorType) = new Client[Encoder, Decoder, PickleType, Result, ErrorType](transport)
}

object Client {
  def apply[Encoder[_], Decoder[_], PickleType] = new ClientFactory[Encoder, Decoder, PickleType]
}
