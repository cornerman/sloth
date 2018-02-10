package sloth

import util._

package object compat {
  implicit class RichTry[T](val s:Try[T]) extends AnyVal {
    def toEither: Either[Throwable, T] = s match {
      case s:Success[T] => s.toEither
      case f:Failure[T] => f.toEither
    }
  }
  implicit class RichFailure[T](val f:Failure[T]) extends AnyVal {
    def toEither: Either[Throwable, T] = Left(f.exception)
  }
  implicit class RichSuccess[T](val s:Success[T]) extends AnyVal {
    def toEither: Either[Throwable, T] = Right(s.value)
  }
}
