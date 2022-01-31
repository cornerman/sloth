package sloth

sealed trait ServerFailure
object ServerFailure {
  case class PathNotFound(path: List[String]) extends ServerFailure
  case class HandlerError(ex: Throwable) extends ServerFailure
  case class DeserializerError(ex: Throwable) extends ServerFailure
}

sealed trait ClientFailure {
  def toException = ClientException(this)
}
object ClientFailure {
  case class TransportError(ex: Throwable) extends ClientFailure
  case class DeserializerError(ex: Throwable) extends ClientFailure

}
case class ClientException(failure: ClientFailure) extends Exception(failure.toString)

trait ClientFailureHandler[PickleType, F[_]] {
  def raiseFailure[B](failure: ClientFailure): F[B]
  def eitherMap[B](fa: F[PickleType])(f: PickleType => Either[ClientFailure, B]): F[B]
}
object ClientFailureHandler {
  import cats.MonadError

  implicit def monadError[PickleType, F[_], ErrorType](implicit me: MonadError[F, ErrorType], c: ClientFailureConvert[ErrorType]): ClientFailureHandler[PickleType, F] = new ClientFailureHandler[PickleType, F] {
    override def raiseFailure[B](failure: ClientFailure): F[B] = me.raiseError(c.convert(failure))
    override def eitherMap[B](fa: F[PickleType])(f: PickleType => Either[ClientFailure, B]): F[B] = me.flatMap(fa)(pt => f(pt).fold(raiseFailure(_), me.pure(_)))
  }
}

trait ClientFailureConvert[+T] {
  def convert(failure: ClientFailure): T
}
object ClientFailureConvert {
  implicit def ToClientFailure: ClientFailureConvert[ClientFailure] = new ClientFailureConvert[ClientFailure] {
    override def convert(failure: ClientFailure) = failure
  }
  implicit def ToClientException: ClientFailureConvert[ClientException] = new ClientFailureConvert[ClientException] {
    override def convert(failure: ClientFailure) = failure.toException
  }
}
