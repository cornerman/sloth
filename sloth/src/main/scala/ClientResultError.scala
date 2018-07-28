package sloth

import cats.MonadError
import cats.syntax.all._

//TODO: is there a typeclass in cats for this "simpler" version of ApplicativeError and mapMaybe?
trait ClientResultErrorT[Result[_], ErrorType] {
  def mapMaybe[T, R](result: Result[T])(f: T => Either[ErrorType, R]): Result[R]
  def raiseError[T](failure: ErrorType): Result[T]
}
object ClientResultErrorT {
  implicit def fromMonadError[Result[_], ErrorType](implicit monad: MonadError[Result, ErrorType]): ClientResultErrorT[Result, ErrorType] = new ClientResultErrorT[Result, ErrorType] {
    def mapMaybe[T, R](result: Result[T])(f: T => Either[ErrorType, R]): Result[R] = result.flatMap(f andThen {
      case Right(v) => monad.pure(v)
      case Left(err) => monad.raiseError(err)
    })
    def raiseError[T](failure: ErrorType): Result[T] = monad.raiseError[T](failure)
  }
}

sealed trait ClientResultError[Result[_]] extends ClientResultErrorT[Result, ClientFailure]
object ClientResultError {
  implicit def fromClientResultErrorT[Result[_], ErrorType](implicit c: ClientResultErrorT[Result, ErrorType], converter: ClientFailureConvert[ErrorType]): ClientResultError[Result] = new ClientResultError[Result] {
    def mapMaybe[T, R](result: Result[T])(f: T => Either[ClientFailure, R]): Result[R] = c.mapMaybe(result)(f andThen (_.left.map(converter.convert)))
    def raiseError[T](failure: ClientFailure): Result[T] = c.raiseError[T](converter.convert(failure))
  }
}
