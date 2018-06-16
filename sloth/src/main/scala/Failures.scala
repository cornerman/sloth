package sloth

sealed trait ServerFailure
object ServerFailure {
  case class PathNotFound(path: List[String]) extends ServerFailure
  case class HandlerError(ex: Throwable) extends ServerFailure
  case class DeserializerError(ex: Throwable) extends ServerFailure
}

sealed trait ClientFailure
object ClientFailure {
  case class TransportError(ex: Throwable) extends ClientFailure
  case class DeserializerError(ex: Throwable) extends ClientFailure
}
case class ClientException(failure: ClientFailure) extends Exception(failure.toString)

trait ClientFailureConvert[+T] {
  def convert(failure: ClientFailure): T
}
object ClientFailureConvert {
  implicit def ToClientFailure: ClientFailureConvert[ClientFailure] = new ClientFailureConvert[ClientFailure] {
    override def convert(failure: ClientFailure) = failure
  }
  implicit def ToClientException: ClientFailureConvert[ClientException] = new ClientFailureConvert[ClientException] {
    override def convert(failure: ClientFailure) = ClientException(failure)
  }
}
