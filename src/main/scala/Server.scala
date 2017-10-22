package apitrait.server

import apitrait.core._
import apitrait.macros.RouterMacro

class Server[Pickler[_], Result[_], PickleType](
  val serializer: Serializer[Pickler, PickleType],
  val canMap: CanMap[Result]) {

  type Router = PartialFunction[Request[PickleType], Result[PickleType]]

  def route[T](impl: T): Router = macro RouterMacro.impl[T, Pickler, Result, PickleType]
}
