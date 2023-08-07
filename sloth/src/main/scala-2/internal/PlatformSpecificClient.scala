package sloth.internal

trait PlatformSpecificClientCo[PickleType, Result[_]] {
  def wire[T]: T = macro TraitMacro.impl[T, PickleType, Result]
}

trait PlatformSpecificClientContra[PickleType, Result[_]] {
  def wire[T]: T = macro TraitMacro.implContra[T, PickleType, Result]
}
