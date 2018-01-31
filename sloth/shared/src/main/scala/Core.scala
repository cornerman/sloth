package sloth.core

case class Request[T](path: List[String], payload: T)

sealed trait ServerFailure
object ServerFailure {
  case class PathNotFound(path: List[String]) extends ServerFailure
  case class HandlerError(ex: Throwable) extends ServerFailure
  case class DeserializerError(ex: Throwable) extends ServerFailure
  implicit class ServerException(failure: ServerFailure) extends Exception(failure.toString)
}
sealed trait ClientFailure
object ClientFailure {
  case class TransportError(ex: Throwable) extends ClientFailure
  case class DeserializerError(ex: Throwable) extends ClientFailure
  implicit class ClientException(failure: ClientFailure) extends Exception(failure.toString)
}
