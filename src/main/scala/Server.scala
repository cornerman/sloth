package apitrait.server

import apitrait.core._
import apitrait.macros.RouterMacro

trait ServerBridge[Pickler[_], Result[_], PickleType] {
  def serialize[T : Pickler](arg: Result[T]): Result[PickleType]
  def deserialize[T : Pickler](arg: PickleType): T
}

class Server[Pickler[_], Result[_], PickleType](val bridge: ServerBridge[Pickler, Result, PickleType]) {
  type Router = PartialFunction[Request[PickleType], Result[PickleType]]
  def route[T](impl: T): Router = macro RouterMacro.impl[T, Pickler, Result, PickleType]
}
