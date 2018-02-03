package sloth.internal

import sloth.core._
import sloth.client.Client
import sloth.server.Server

import chameleon._
import cats.syntax.all._

import scala.util.{Success, Failure, Try}

class ServerImpl[PickleType, Result[_]](server: Server[PickleType, Result]) {
  import server._

  def execute[T, R](path: List[String], arguments: PickleType)(call: T => Result[R])(implicit deserializer: Deserializer[T, PickleType], serializer: Serializer[R, PickleType]): Either[ServerFailure, Result[PickleType]] = {
    deserializer.deserialize(arguments) match {
      case Right(arguments) =>
        Try(call(arguments)) match {
          case Success(result) =>
            logger.logRequest(path, arguments, Right(result))
            Right(result.map { value =>
              logger.logSuccess(path, arguments, value)
              serializer.serialize(value)
            })
          case Failure(err) =>
            val result = Left(ServerFailure.HandlerError(err))
            logger.logRequest(path, arguments, result)
            result
        }
      case Left(err)   =>
        val result = Left(ServerFailure.DeserializerError(err))
        logger.logRequest(path, arguments, result)
        result
    }
  }
}

class ClientImpl[PickleType, Result[_], ErrorType](client: Client[PickleType, Result, ErrorType]) {
  import client._

  def execute[T, R](path: List[String], arguments: T)(implicit deserializer: Deserializer[R, PickleType], serializer: Serializer[T, PickleType]): Result[R] = {
    val params = serializer.serialize(arguments)
    val request: Request[PickleType] = Request(path, params)
    val result: Result[R] = Try(transport(request)) match {
      case Success(response) => response.flatMap { response =>
        deserializer.deserialize(response) match {
          case Right(value) =>
            logger.logSuccess(path, arguments, value)
            monad.pure[R](value)
          case Left(t)     => monad.raiseError(failureConverter.convert(ClientFailure.DeserializerError(t)))
        }
      }
      case Failure(t) => monad.raiseError(failureConverter.convert(ClientFailure.TransportError(t)))
    }

    logger.logRequest(path, arguments, result)
    result
  }
}
