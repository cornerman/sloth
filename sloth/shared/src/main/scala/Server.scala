package sloth.server

import sloth.core._
import sloth.internal.RouterMacro

import cats.Functor

class Server[PickleType, Result[_]](implicit private[sloth] val functor: Functor[Result]) {

  def route[T](value: T): Server.Router[PickleType, Result] = macro RouterMacro.impl[T, PickleType, Result]
}

object Server {
  type Router[PickleType, Result[_]] = Request[PickleType] => Either[SlothServerFailure, Result[PickleType]]

  def apply[PickleType, Result[_]](implicit functor: Functor[Result]) = new Server[PickleType, Result]
}
