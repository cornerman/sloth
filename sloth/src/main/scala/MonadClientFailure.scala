package sloth

import cats.MonadError
import scala.annotation.implicitNotFound
import cats.syntax.all._

@implicitNotFound(msg = "Cannot find implicit MonadClientFailure[${Result}]. Make sure there is an implicit cats.MonadError[${Result}, _ >: ErrorType] and a sloth.ClientFailureConvert[ErrorType] for some ErrorType, or define a MonadClientFailure yourself.")
sealed trait MonadClientFailure[Result[_]] {
  def mapMaybe[T, R](result: Result[T])(f: T => Either[ClientFailure, R]): Result[R]
  def raiseError[T](failure: ClientFailure): Result[T]
}
object MonadClientFailure {
  implicit def fromMonadError[Result[_], ErrorType](implicit monad: MonadError[Result, ErrorType], converter: ClientFailureConvert[ErrorType]): MonadClientFailure[Result] = new MonadClientFailure[Result] {
    def mapMaybe[T, R](result: Result[T])(f: T => Either[ClientFailure, R]): Result[R] = result.flatMap(f andThen {
      case Right(v) => monad.pure(v)
      case Left(err) => raiseError(err)
    })
    def raiseError[T](failure: ClientFailure): Result[T] = monad.raiseError[T](converter.convert(failure))
  }
}
