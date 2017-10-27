package sloth.client

import cats.MonadError
import sloth.core._
import sloth.macros.TraitMacro

class Client[Encoder[_], Decoder[_], PickleType, Result[_], ErrorType] private(
  transport: RequestTransport[PickleType, Result])(implicit
  serializer: Serializer[Encoder, Decoder, PickleType],
  monad: MonadError[Result, _ >: ErrorType],
  failureIsError: SlothFailure => ErrorType) {

  def wire[T]: T = macro TraitMacro.impl[T, PickleType, Result]

  def execute[T : Encoder, R : Decoder](path: List[String], arguments: T): Result[R] = {
    val params = serializer.serialize[T](arguments)
    val result = transport(Request[PickleType](path, params))
    monad.flatMap(result) { result =>
      serializer.deserialize[R](result) match {
        case Left(err) => monad.raiseError(SlothFailure.DeserializationError(err))
        case Right(result) => monad.pure[R](result)
      }
    }
  }
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
