package sloth.internal

import sloth.core._
import sloth.client._
import sloth.server._

import chameleon._
import cats.syntax.all._

import scala.util.{Success, Failure, Try}

class ServerImpl[PickleType, Result[_]](server: Server[PickleType, Result]) {
  import server._

  def execute[T, R](arguments: PickleType)(call: T => Result[R])(implicit deserializer: Deserializer[T, PickleType], serializer: Serializer[R, PickleType]): Either[ServerFailure, Result[PickleType]] = {
    deserializer.deserialize(arguments) match {
      case Right(args) => Try(call(args)) match {
        case Success(result) => Right(result.map(x => serializer.serialize(x)))
        case Failure(err) => Left(ServerFailure.HandlerError(err))
      }
      case Left(err)   => Left(ServerFailure.DeserializerError(err))
    }
  }
}

class ClientImpl[PickleType, Result[_], ErrorType](client: Client[PickleType, Result, ErrorType], transport: RequestTransport[PickleType, Result]) {
  import client._

  def execute[T, R](path: List[String], arguments: T)(implicit deserializer: Deserializer[R, PickleType], serializer: Serializer[T, PickleType]): Result[R] = {
    val params = serializer.serialize(arguments)
    val request = Request(path, params)
    Try(transport(request)) match {
      case Success(response) => response.flatMap { response =>
        deserializer.deserialize(response) match {
          case Right(result) => monad.pure[R](result)
          case Left(err)     => monad.raiseError(ClientFailure.DeserializerError(err))
        }
      }
      case Failure(err) => monad.raiseError(ClientFailure.TransportError(err))
    }
  }
}
