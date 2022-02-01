package sloth

trait ClientHandler[F[_]] {
  def raiseFailure[B](failure: ClientFailure): F[B]
  def eitherMap[A,B](fa: F[A])(f: A => Either[ClientFailure, B]): F[B]
}
object ClientHandler {
  import cats.MonadError

  implicit def monadError[F[_], ErrorType](implicit me: MonadError[F, ErrorType], c: ClientFailureConvert[ErrorType]): ClientHandler[F] = new ClientHandler[F] {
    override def raiseFailure[B](failure: ClientFailure): F[B] = me.raiseError(c.convert(failure))
    override def eitherMap[A,B](fa: F[A])(f: A => Either[ClientFailure, B]): F[B] = me.flatMap(fa)(pt => f(pt).fold(raiseFailure(_), me.pure(_)))
  }
}

trait ClientContraHandler[F[_]] {
  def raiseFailure[B](failure: ClientFailure): F[B]
  def contramap[A,B](response: F[A])(f: B => A): F[B]
}
object ClientContraHandler {
  import cats.ApplicativeError
  import cats.data.Kleisli

  implicit def applicativeErrorKleisli[R, F[_], ErrorType](implicit me: ApplicativeError[F, ErrorType], c: ClientFailureConvert[ErrorType]): ClientContraHandler[Kleisli[F, *, R]] = new ClientContraHandler[Kleisli[F, *, R]] {
    override def raiseFailure[B](failure: ClientFailure): Kleisli[F,B,R] = Kleisli.liftF(me.raiseError(c.convert(failure)))
    override def contramap[A,B](fa: Kleisli[F,A,R])(f: B => A): Kleisli[F,B,R] = Kleisli(b => fa(f(b)))
  }

  implicit def applicativeErrorFunc[R, F[_], ErrorType](implicit me: ApplicativeError[F, ErrorType], c: ClientFailureConvert[ErrorType]): ClientContraHandler[Function1[*, F[R]]] = new ClientContraHandler[Function1[*, F[R]]] {
    override def raiseFailure[B](failure: ClientFailure): B => F[R] = _ => me.raiseError(c.convert(failure))
    override def contramap[A,B](fa: A => F[R])(f: B => A): B => F[R] = b => fa(f(b))
  }
}
