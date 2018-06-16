package sloth

import cats.{Monad, MonadError}

sealed trait MonadErrorProvider[Result[_]] {
  def monad: Monad[Result]
  def raiseError[T](failure: ClientFailure): Result[T]
}
object MonadErrorProvider {
  class Converted[Result[_], ErrorType](implicit val monad: MonadError[Result, _ >: ErrorType], converter: ClientFailureConvert[ErrorType]) extends MonadErrorProvider[Result] {
    def raiseError[T](failure: ClientFailure): Result[T] = monad.raiseError[T](converter.convert(failure))
  }

  implicit def clientFailure[Result[_]](implicit monadError: MonadError[Result, _ >: ClientFailure]): MonadErrorProvider[Result] = new Converted[Result, ClientFailure]
  implicit def clientException[Result[_]](implicit monadError: MonadError[Result, _ >: ClientException]): MonadErrorProvider[Result] = new Converted[Result, ClientException]
}

