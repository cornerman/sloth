package sloth

import sloth.internal.ChecksumMacro

object ChecksumCalculator {
  final def checksumOf[T]: Int = macro ChecksumMacro.impl[T]
}
