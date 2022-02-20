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

  implicit def applicativeErrorFunc[R, F[_], ErrorType](implicit me: ApplicativeError[F, ErrorType], c: ServerFailureConvert[ErrorType]): RouterContraHandler[Function1[*, F[R]]] = new RouterContraHandler[Function1[*, F[R]]] {
    override def eitherContramap[A,B](fa: A => F[R])(f: B => Either[ServerFailure, A]): B => F[R] = { b =>
      f(b) match {
        case Right(a) => fa(a)
        case Left(error) => me.raiseError(c.convert(error))
      }
    }
  }
}
