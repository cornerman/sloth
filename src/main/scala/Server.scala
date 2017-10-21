package apitrait.server

import apitrait.core._
import apitrait.macros.RouterMacro

trait ServerBridge[Pickler[_], Result[_], PickleType] extends{
  def serialize[T : Pickler](result: Result[T]): Result[PickleType]
  def deserialize[T : Pickler](args: PickleType): T
}

class Server[Pickler[_], Result[_], PickleType](val bridge: ServerBridge[Pickler, Result, PickleType]) {
  type Router = PartialFunction[Request[PickleType], Result[PickleType]]
  def route[T](impl: T): Router = macro RouterMacro.impl[T, Pickler, Result, PickleType]
}
