package sloth

import cats.MonadError
import scala.annotation.implicitNotFound
import cats.syntax.all._

@implicitNotFound(msg = "Cannot find implicit ClientResultError[${Result}]. Make sure there is an implicit cats.MonadError[${Result}, _ >: ErrorType] and a sloth.ClientFailureConvert[ErrorType] for some ErrorType, or define a ClientResultErrorT yourself.")
sealed trait ClientResultError[Result[_]] {
  def mapMaybe[T, R](result: Result[T])(f: T => Either[ClientFailure, R]): Result[R]
  def raiseError[T](failure: ClientFailure): Result[T]
}
object ClientResultError {
  implicit def fromMonadError[Result[_], ErrorType](implicit monad: MonadError[Result, ErrorType], converter: ClientFailureConvert[ErrorType]): ClientResultError[Result] = new ClientResultError[Result] {
    def mapMaybe[T, R](result: Result[T])(f: T => Either[ClientFailure, R]): Result[R] = result.flatMap(f andThen {
      case Right(v) => monad.pure(v)
      case Left(err) => raiseError(err)
    })
    def raiseError[T](failure: ClientFailure): Result[T] = monad.raiseError[T](converter.convert(failure))
  }

  implicit def fromClientResultT[Result[_], ErrorType](implicit c: ClientResultErrorT[Result, ErrorType], converter: ClientFailureConvert[ErrorType]): ClientResultError[Result] = new ClientResultError[Result] {
    def mapMaybe[T, R](result: Result[T])(f: T => Either[ClientFailure, R]): Result[R] = c.mapMaybe(result)(f andThen (_.left.map(converter.convert)))
    def raiseError[T](failure: ClientFailure): Result[T] = c.raiseError[T](converter.convert(failure))
  }

}

//TODO: is there a typeclass in cats for this "simpler" version of monaderror/applicativerror?
trait ClientResultErrorT[Result[_], ErrorType] {
  def mapMaybe[T, R](result: Result[T])(f: T => Either[ErrorType, R]): Result[R]
  def raiseError[T](failure: ErrorType): Result[T]
}
