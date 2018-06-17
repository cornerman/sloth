package sloth

import cats.{Monad, MonadError}
import scala.annotation.implicitNotFound

@implicitNotFound(msg = "Cannot find implicit MonadClientFailure[$Result]. Make sure there is an implicit cats.MonadError[$Result, ErrorType] and a sloth.ClientFailureConvert[ErrorType] forSome ErrorType.")
sealed trait MonadClientFailure[Result[_]] {
  implicit def monad: MonadError[Result, _]
  def raiseError[T](failure: ClientFailure): Result[T]
}
object MonadClientFailure {
  implicit def FromMonadError[Result[_], ErrorType](implicit monadError: MonadError[Result, ErrorType], converter: ClientFailureConvert[ErrorType]): MonadClientFailure[Result] = new MonadClientFailure[Result] {
    def monad = monadError
    def raiseError[T](failure: ClientFailure): Result[T] = monad.raiseError[T](converter.convert(failure))
  }
}
