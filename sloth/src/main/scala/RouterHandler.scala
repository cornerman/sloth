package sloth

trait RouterContraHandler[F[_]] {
  def eitherContramap[A,B](fa: F[A])(f: B => Either[ServerFailure, A]): F[B]
}
object RouterContraHandler {
  import cats.ApplicativeError
  import cats.data.Kleisli

  implicit def applicativeErrorKleisli[R, F[_], ErrorType](implicit me: ApplicativeError[F, ErrorType], c: ServerFailureConvert[ErrorType]): RouterContraHandler[Kleisli[F, *, R]] = new RouterContraHandler[Kleisli[F, *, R]] {
    override def eitherContramap[A,B](fa: Kleisli[F, A, R])(f: B => Either[ServerFailure, A]): Kleisli[F, B, R] = Kleisli { b =>
      f(b) match {
        case Right(a) => fa(a)
        case Left(error) => me.raiseError(c.convert(error))
      }
    }
  }

  implicit def applicativeErrorFunc[R, F[_], ErrorType](implicit me: ApplicativeError[F, ErrorType], c: ServerFailureConvert[ErrorType]): RouterContraHandler[* => F[R]] = new RouterContraHandler[* => F[R]] {
    override def eitherContramap[A,B](fa: A => F[R])(f: B => Either[ServerFailure, A]): B => F[R] = { b =>
      f(b) match {
        case Right(a) => fa(a)
        case Left(error) => me.raiseError(c.convert(error))
      }
    }
  }

  implicit def applicativeErrorKleisli2[I, R, F[_], ErrorType](implicit me: ApplicativeError[F, ErrorType], c: ServerFailureConvert[ErrorType]): RouterContraHandler[λ[A => Kleisli[F, (I, A), R]]] = new RouterContraHandler[λ[A => Kleisli[F, (I, A), R]]] {
    override def eitherContramap[A,B](fa: Kleisli[F, (I, A), R])(f: B => Either[ServerFailure, A]): Kleisli[F, (I, B), R] = Kleisli { case (i, b) =>
      f(b) match {
        case Right(a) => fa((i, a))
        case Left(error) => me.raiseError(c.convert(error))
      }
    }
  }

  implicit def applicativeErrorFunc2[I, R, F[_], ErrorType](implicit me: ApplicativeError[F, ErrorType], c: ServerFailureConvert[ErrorType]): RouterContraHandler[λ[A => (I, A) => F[R]]] = new RouterContraHandler[λ[A => (I, A) => F[R]]] {
    override def eitherContramap[A,B](fa: (I, A) => F[R])(f: B => Either[ServerFailure, A]): (I, B) => F[R] = { (i, b) =>
      f(b) match {
        case Right(a) => fa(i, a)
        case Left(error) => me.raiseError(c.convert(error))
      }
    }
  }
}
