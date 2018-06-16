package sloth

import cats.{Monad, MonadError}

sealed trait MonadClientFailure[Result[_]] extends Monad[Result] {
  def raiseError[T](failure: ClientFailure): Result[T]
}
object MonadClientFailure {
  def apply[Result[_], ErrorType](implicit monad: MonadError[Result, _ >: ErrorType], converter: ClientFailureConvert[ErrorType]) =
    new MonadClientFailure[Result] {
      def raiseError[T](failure: ClientFailure): Result[T] = monad.raiseError[T](converter.convert(failure))
      def pure[A](x: A): Result[A] = monad.pure(x)
      def flatMap[A, B](fa: Result[A])(f: A => Result[B]): Result[B] = monad.flatMap(fa)(f)
      def tailRecM[A, B](a: A)(f: A => Result[Either[A,B]]): Result[B] = monad.tailRecM(a)(f)
    }
}
