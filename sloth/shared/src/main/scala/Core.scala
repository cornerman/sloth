package sloth.core

trait Writer[Type, PickleType] {
  def write(arg: Type): PickleType
}
trait Reader[Type, PickleType] {
  def read(arg: PickleType): Either[Throwable, Type]
}

case class Request[T](path: List[String], payload: T)

sealed trait SlothServerFailure
object SlothServerFailure {
  case class PathNotFound(path: List[String]) extends SlothServerFailure
  case class HandlerError(ex: Throwable) extends SlothServerFailure
  case class ReaderError(ex: Throwable) extends SlothServerFailure
  implicit class SlothException(failure: SlothServerFailure) extends Exception(failure.toString)
}
sealed trait SlothClientFailure
object SlothClientFailure {
  case class TransportError(ex: Throwable) extends SlothClientFailure
  case class ReaderError(ex: Throwable) extends SlothClientFailure
  implicit class SlothException(failure: SlothClientFailure) extends Exception(failure.toString)
}
