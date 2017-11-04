package sloth.core

import scala.util.control.NoStackTrace

trait Serializer[Encoder[_], Decoder[_], PickleType] {
  def serialize[T : Encoder](arg: T): PickleType
  def deserialize[T : Decoder](arg: PickleType): Either[Throwable, T]
}

case class Request[T](path: List[String], payload: T)

trait RequestTransport[PickleType, Result[_]] {
  def apply(request: Request[PickleType]): Result[PickleType]
}

//TODO split into server and client errors
//TODO should we catch handler code and return an unexpected error with a throwable?
sealed trait SlothFailure extends NoStackTrace
object SlothFailure {
  case class DeserializationError(ex: Throwable) extends SlothFailure
  case class PathNotFound(path: List[String]) extends SlothFailure
}
