package apitrait.client

import apitrait.macros.TraitMacro

trait ClientBridge[Pickler[_], Result[_], PickleType] {
  def call[T : Pickler, R](path: List[String], arguments: T): Result[R]
}

class Client[Pickler[_], Result[_], PickleType](val bridge: ClientBridge[Pickler, Result, PickleType]) {
  def wired[T]: T = macro TraitMacro.impl[T, Pickler, Result, PickleType]
}
