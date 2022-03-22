package sloth.types

trait FlatMapError[F[_], E] {
  def raiseError[B](failure: E): F[B]
  def flatMapEither[A,B](fa: F[A])(f: A => Either[E, B]): F[B]
}
object FlatMapError {
  import cats.MonadError

  implicit def monadError[F[_], ErrorType](implicit me: MonadError[F, ErrorType]): FlatMapError[F, ErrorType] = new FlatMapError[F, ErrorType] {
    override def raiseError[B](failure: ErrorType): F[B] = me.raiseError(failure)
    override def flatMapEither[A,B](fa: F[A])(f: A => Either[ErrorType, B]): F[B] = me.flatMap(fa)(pt => f(pt).fold(raiseError(_), me.pure(_)))
  }
}
