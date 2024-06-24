package sloth

sealed trait ServerFailure {
  def toException = ServerException(this)
}
object ServerFailure {
  case class EndpointNotFound(path: Endpoint) extends ServerFailure
  case class HandlerError(ex: Throwable) extends ServerFailure
  case class DeserializerError(ex: Throwable) extends ServerFailure

  @deprecated("Use EndpointNotFound instead", "0.8.0")
  val PathNotFound = EndpointNotFound
  @deprecated("Use EndpointNotFound instead", "0.8.0")
  type PathNotFound = EndpointNotFound
}

case class ServerException(failure: ServerFailure) extends Exception(failure.toString)

sealed trait ClientFailure {
  def toException = ClientException(this)
}
object ClientFailure {
  case class TransportError(ex: Throwable) extends ClientFailure
  case class DeserializerError(ex: Throwable) extends ClientFailure
}
case class ClientException(failure: ClientFailure) extends Exception(failure.toString)

trait ServerFailureConvert[+T] {
  def convert(failure: ServerFailure): T
}
object ServerFailureConvert {
  implicit def ToServerFailure: ServerFailureConvert[ServerFailure] = new ServerFailureConvert[ServerFailure] {
    override def convert(failure: ServerFailure) = failure
  }
  implicit def ToServerException: ServerFailureConvert[ServerException] = new ServerFailureConvert[ServerException] {
    override def convert(failure: ServerFailure) = failure.toException
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
