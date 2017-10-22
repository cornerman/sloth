package apitrait.client

import apitrait.core._
import apitrait.macros.TraitMacro

trait ClientBridge[Pickler[_], Result[_], PickleType] {
  def serialize[T : Pickler](arg: T): PickleType
  def deserialize[T : Pickler](arg: Result[PickleType]): Result[T]
  def call(request: Request[PickleType]): Result[PickleType]
}

class Client[Pickler[_], Result[_], PickleType](val bridge: ClientBridge[Pickler, Result, PickleType]) {
  def wire[T]: T = macro TraitMacro.impl[T, Pickler, Result, PickleType]
}
