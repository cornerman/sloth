package sloth.internal

import sloth.core._
import sloth.client.Client
import sloth.server.{Server, ServerResult}

import chameleon._
import cats.syntax.all._
import shapeless._
import shapeless.ops.hlist._

import scala.util.{Success, Failure, Try}

class ServerImpl[PickleType, Result[_]](server: Server[PickleType, Result]) {
  import server._

  def execute[T <: HList, R](path: List[String], arguments: PickleType)(call: T => Result[R])(implicit deserializer: Deserializer[T, PickleType], serializer: Serializer[R, PickleType], ev: ToTraversable.Aux[T, List, HList]): ServerResult[Result, PickleType] = {
    deserializer.deserialize(arguments) match {
      case Right(arguments) =>
        val paramsList = arguments.toList.map(_.runtimeList)
        Try(call(arguments)) match {
          case Success(result) =>
            ServerResult.Success(paramsList, result.map { value =>
              ServerResult.Value(value, serializer.serialize(value))
            })
          case Failure(err) => ServerResult.Failure[PickleType](paramsList, ServerFailure.HandlerError(err))
        }
      case Left(err) => ServerResult.Failure[PickleType](Nil, ServerFailure.DeserializerError(err))
    }
  }
}

class ClientImpl[PickleType, Result[_], ErrorType](client: Client[PickleType, Result, ErrorType]) {
  import client._

  def execute[T <: HList, R](path: List[String], arguments: T)(implicit deserializer: Deserializer[R, PickleType], serializer: Serializer[T, PickleType], ev: ToTraversable.Aux[T, List, HList]): Result[R] = {
    val paramsList = arguments.toList.map(_.runtimeList)
    val serializedArguments = serializer.serialize(arguments)
    val request: Request[PickleType] = Request(path, serializedArguments)
    val result: Result[R] = Try(transport(request)) match {
      case Success(response) => response.flatMap { response =>
        deserializer.deserialize(response) match {
          case Right(value) => monad.pure[R](value)
          case Left(t) => monad.raiseError(failureConverter.convert(ClientFailure.DeserializerError(t)))
        }
      }
      case Failure(t) => monad.raiseError(failureConverter.convert(ClientFailure.TransportError(t)))
    }

    logger.logRequest(path, paramsList, result)
    result
  }
}
