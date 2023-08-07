package sloth.internal

import sloth.{RouterCo, RouterContra}

trait PlatformSpecificRouterCo[PickleType, Result[_]] { self: RouterCo[PickleType, Result] =>
  inline def route[T](value: T): RouterCo[PickleType, Result] = ${ RouterMacro.impl[T, PickleType, Result]('self, 'value) }
}

trait PlatformSpecificRouterContra[PickleType, Result[_]] { self: RouterContra[PickleType, Result] =>
  inline def route[T](value: T): RouterContra[PickleType, Result] = ${ RouterMacro.implContra[T, PickleType, Result]('self, 'value) }
}
