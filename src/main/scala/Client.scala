package apitrait.client

import apitrait.core._
import apitrait.macros.TraitMacro

class Client[Pickler[_], Result[_], PickleType](
  val serializer: Serializer[Pickler, PickleType],
  val canMap: CanMap[Result],
  val transport: RequestTransport[Result, PickleType]) {

  def wire[T]: T = macro TraitMacro.impl[T, Pickler, Result, PickleType]
}
