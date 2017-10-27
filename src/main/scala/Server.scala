package sloth.server

import cats.Functor
import sloth.core._
import sloth.macros.RouterMacro

class Server[Encoder[_], Decoder[_], PickleType, Result[_]] private(implicit
  serializer: Serializer[Encoder, Decoder, PickleType],
  functor: Functor[Result]) {

  def route[T](impl: T): Server.Router[PickleType, Result] = macro RouterMacro.impl[T, PickleType, Result]

  def execute[T : Decoder, R : Encoder](arguments: PickleType)(call: T => Result[R]): Either[SlothFailure, Result[PickleType]] = {
    serializer.deserialize[T](arguments) match {
      case Left(err) => Left(SlothFailure.DeserializationError(err))
      case Right(args) => Right(functor.map(call(args))(x => serializer.serialize[R](x)))
    }
  }
}

object Server {
  type Router[PickleType, Result[_]] = Request[PickleType] => Either[SlothFailure, Result[PickleType]]

  def apply[Encoder[_], Decoder[_], PickleType, Result[_]](implicit
    serializer: Serializer[Encoder, Decoder, PickleType],
    functor: Functor[Result]) = new Server[Encoder, Decoder, PickleType, Result]
}
