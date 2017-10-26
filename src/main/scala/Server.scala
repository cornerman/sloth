package sloth.server

import sloth.core._
import sloth.macros.RouterMacro

class Server[Pickler[_], Result[_], PickleType](
  val serializer: Serializer[Pickler, PickleType],
  val canMap: CanMap[Result]) {

  type Router = PartialFunction[Request[PickleType], Result[PickleType]]

  def route[T](impl: T): Router = macro RouterMacro.impl[T, Result, PickleType]
}
