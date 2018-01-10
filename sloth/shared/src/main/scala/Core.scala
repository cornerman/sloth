package sloth.core

trait Writer[Type, PickleType] {
  def write(arg: Type): PickleType
}
trait Reader[Type, PickleType] {
  def read(arg: PickleType): Either[Throwable, Type]
}

case class Request[T](path: List[String], payload: T)

//TODO: move to client?
trait RequestTransport[PickleType, Result[_]] {
  def apply(request: Request[PickleType]): Result[PickleType]
}
object RequestTransport {
  def apply[PickleType, Result[_]](f: Request[PickleType] => Result[PickleType]) =
    new RequestTransport[PickleType, Result] {
      def apply(request: Request[PickleType]): Result[PickleType] = f(request)
    }
}

sealed trait SlothServerFailure
object SlothServerFailure {
  //TODO should we catch handler code and return an unexpected error with a throwable?
  case class ReaderError(ex: Throwable) extends SlothServerFailure
  implicit class SlothException(failure: SlothServerFailure) extends Exception(failure.toString)
}
sealed trait SlothClientFailure
object SlothClientFailure {
  case class ReaderError(ex: Throwable) extends SlothClientFailure
  implicit class SlothException(failure: SlothClientFailure) extends Exception(failure.toString)
}
