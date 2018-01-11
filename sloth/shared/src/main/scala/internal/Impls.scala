package sloth.internal

import sloth.core._
import sloth.client.Client
import sloth.server.Server

import cats.syntax.all._
import scala.util.{Success, Failure, Try}

class ServerImpl[PickleType, Result[_]](server: Server[PickleType, Result]) {
  import server._

  def execute[T, R](arguments: PickleType)(call: T => Result[R])(implicit reader: Reader[T, PickleType], writer: Writer[R, PickleType]): Either[SlothServerFailure, Result[PickleType]] = {
    reader.read(arguments) match {
      case Right(args) => Try(call(args)) match {
        case Success(result) => Right(result.map(x => writer.write(x)))
        case Failure(err) => Left(SlothServerFailure.HandlerError(err))
      }
      case Left(err)   => Left(SlothServerFailure.ReaderError(err))
    }
  }
}

class ClientImpl[PickleType, Result[_], ErrorType](client: Client[PickleType, Result, ErrorType]) {
  import client._

  def execute[T, R](path: List[String], arguments: T)(implicit reader: Reader[R, PickleType], writer: Writer[T, PickleType]): Result[R] = {
    val params = writer.write(arguments)
    val request = Request(path, params)
    Try(transport(request)) match {
      case Success(response) => response.flatMap { response =>
        reader.read(response) match {
          case Right(result) => monad.pure[R](result)
          case Left(err)     => monad.raiseError(SlothClientFailure.ReaderError(err))
        }
      }
      case Failure(err) => monad.raiseError(SlothClientFailure.TransportError(err))
    }
  }
}
