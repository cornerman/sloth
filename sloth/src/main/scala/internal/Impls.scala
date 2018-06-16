package sloth.internal

import sloth._
import chameleon._
import cats.Functor
import cats.syntax.all._

import scala.util.{Success, Failure, Try}

class RouterImpl[PickleType, Result[_] : Functor] {
  def execute[T <: Product, R](path: List[String], arguments: PickleType)(call: T => Result[R])(implicit deserializer: Deserializer[T, PickleType], serializer: Serializer[R, PickleType]): RouterResult[PickleType, Result] = {
    deserializer.deserialize(arguments) match {
      case Right(arguments) =>
        Try(call(arguments)) match {
          case Success(result) =>
            RouterResult.Success(arguments, result.map { value =>
              RouterResult.Value(value, serializer.serialize(value))
            })
          case Failure(err) => RouterResult.Failure[PickleType, Result](Some(arguments), ServerFailure.HandlerError(err))
        }
      case Left(err) => RouterResult.Failure[PickleType, Result](None, ServerFailure.DeserializerError(err))
    }
  }
}

class ClientImpl[PickleType, Result[_], ErrorType](client: Client[PickleType, Result, ErrorType]) {
  import client._

  def execute[T <: Product, R](path: List[String], arguments: T)(implicit deserializer: Deserializer[R, PickleType], serializer: Serializer[T, PickleType]): Result[R] = {
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

    logger.logRequest(path, arguments, result)
    result
  }
}
