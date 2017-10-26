package sloth.client

import sloth.core._
import sloth.macros.TraitMacro

class Client[Encoder[_], Decoder[_], Result[_], PickleType](
  val serializer: Serializer[Encoder, Decoder, PickleType],
  val canMap: CanMap[Result],
  val transport: RequestTransport[Result, PickleType]) {

  def wire[T]: T = macro TraitMacro.impl[T, Result, PickleType]
}
