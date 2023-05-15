package sloth.internal

import sloth.{ClientCo, ClientContra}

trait PlatformSpecificClientCo[PickleType, Result[_]] { self: ClientCo[PickleType, Result] =>
  inline def wire[T]: T = ${ TraitMacro.impl[T, PickleType, Result]('self) }
}

trait PlatformSpecificClientContra[PickleType, Result[_]] { self: ClientContra[PickleType, Result] =>
  inline def wire[T]: T = ${ TraitMacro.implContra[T, PickleType, Result]('self) }
}
