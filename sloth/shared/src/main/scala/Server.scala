package sloth.server

import sloth.core._
import sloth.internal.RouterMacro

import cats.Functor

class Server[Encoder[_], Decoder[_], PickleType, Result[_]] private(implicit
  private[sloth] val serializer: Serializer[Encoder, Decoder, PickleType],
  private[sloth] val functor: Functor[Result]) {

  def route[T](value: T): Server.Router[PickleType, Result] = macro RouterMacro.impl[T, PickleType, Result]
}

object Server {
  type Router[PickleType, Result[_]] = Request[PickleType] => Either[SlothFailure, Result[PickleType]]

  def apply[Encoder[_], Decoder[_], PickleType, Result[_]](implicit
    serializer: Serializer[Encoder, Decoder, PickleType],
    functor: Functor[Result]) = new Server[Encoder, Decoder, PickleType, Result]
}
