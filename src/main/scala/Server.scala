package sloth.server

import cats.Functor
import sloth.core._
import sloth.macros.RouterMacro

class Server[Encoder[_], Decoder[_], PickleType, Result[_]](implicit
  serializer: Serializer[Encoder, Decoder, PickleType],
  functor: Functor[Result]) {

  type Router = Request[PickleType] => Either[SlothFailure, Result[PickleType]]

  def route[T](impl: T): Router = macro RouterMacro.impl[T, PickleType, Result]

  def execute[T : Decoder, R : Encoder](arguments: PickleType)(call: T => Result[R]): Either[SlothFailure, Result[PickleType]] = {
    serializer.deserialize[T](arguments) match {
      case Left(err) => Left(SlothFailure.DeserializationError(err))
      case Right(args) => Right(functor.map(call(args))(x => serializer.serialize[R](x)))
    }
  }
}
