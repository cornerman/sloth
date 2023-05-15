package sloth.internal

import sloth.{RouterCo, RouterContra}

trait PlatformSpecificRouterCo[PickleType, Result[_]] {
  def route[T](value: T): RouterCo[PickleType, Result] = macro RouterMacro.impl[T, PickleType, Result]
}

trait PlatformSpecificRouterContra[PickleType, Result[_]] {
  def route[T](value: T): RouterContra[PickleType, Result] = macro RouterMacro.implContra[T, PickleType, Result]
}
