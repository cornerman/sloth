package sloth.internal

import sloth.core._
import sloth.client.Client
import sloth.server.Server

class ServerImpl[PickleType, Result[_]](server: Server[PickleType, Result]) {
  import server._

  type Read[T] = Reader[T, PickleType]
  type Write[T] = Writer[T, PickleType]

  def execute[T, R](arguments: PickleType)(call: T => Result[R])(implicit reader: Reader[T, PickleType], writer: Writer[R, PickleType]): Either[SlothFailure, Result[PickleType]] = {
    reader.read(arguments) match {
      case Left(err) => Left(SlothFailure.DeserializationError(err))
      case Right(args) => Right(functor.map(call(args))(x => writer.write(x)))
    }
  }
}

class ClientImpl[PickleType, Result[_], ErrorType](client: Client[PickleType, Result, ErrorType]) {
  import client._

  type Read[T] = Reader[T, PickleType]
  type Write[T] = Writer[T, PickleType]

  def execute[T, R](path: List[String], arguments: T)(implicit reader: Reader[R, PickleType], writer: Writer[T, PickleType]): Result[R] = {
    val params = writer.write(arguments)
    val result = transport(Request[PickleType](path, params))
    monad.flatMap(result) { result =>
      reader.read(result) match {
        case Left(err) => monad.raiseError(SlothFailure.DeserializationError(err))
        case Right(result) => monad.pure[R](result)
      }
    }
  }
}
