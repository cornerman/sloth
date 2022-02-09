package sloth.internal

import sloth._
import chameleon._
import cats.Functor
import cats.syntax.all._

import scala.util.{Success, Failure, Try}

class RouterImpl[PickleType, Result[_] : Functor](router: Router[PickleType, Result]) {
  def execute[T, R](path: List[String], arguments: PickleType)(call: T => Result[R])(implicit deserializer: Deserializer[T, PickleType], serializer: Serializer[R, PickleType]): RouterResult[PickleType, Result] = {
    deserializer.deserialize(arguments) match {
      case Right(arguments) =>
        Try(call(arguments)) match {
          case Success(result) =>
            RouterResult.Success(arguments, router.logger.logRequest[R](path, arguments, result).map { value =>
              RouterResult.Value(value, serializer.serialize(value))
            })
          case Failure(err) => RouterResult.Failure[PickleType](Some(arguments), ServerFailure.HandlerError(err))
        }
      case Left(err) => RouterResult.Failure[PickleType](None, ServerFailure.DeserializerError(err))
    }
  }
}

class ClientImpl[PickleType, Result[_]](client: ClientCo[PickleType, Result]) {

  def execute[T, R](path: List[String], arguments: T)(implicit deserializer: Deserializer[R, PickleType], serializer: Serializer[T, PickleType]): Result[R] = {
    val serializedArguments = serializer.serialize(arguments)
    val request: Request[PickleType] = Request(path, serializedArguments)
    val result: Result[R] = Try(client.transport(request)) match {
      case Success(response) => client.failureHandler.eitherMap(response) { response =>
        deserializer.deserialize(response) match {
          case Right(value) => Right(value)
          case Left(t) => Left(ClientFailure.DeserializerError(t))
        }
      }
      case Failure(t) => client.failureHandler.raiseFailure(ClientFailure.TransportError(t))
    }

    client.logger.logRequest[R](path, arguments, result)
  }
}

class ClientContraImpl[PickleType, Result[_]](client: ClientContra[PickleType, Result]) {

  def execute[T, R](path: List[String], arguments: T)(implicit serializerR: Serializer[R, PickleType], serializerT: Serializer[T, PickleType]): Result[R] = {
    val serializedArguments = serializerT.serialize(arguments)
    val request: Request[PickleType] = Request(path, serializedArguments)
    val result: Result[R] = Try(client.transport(request)) match {
      case Success(response) => client.failureHandler.contramap(response)(serializerR.serialize(_))
      case Failure(t) => client.failureHandler.raiseFailure(ClientFailure.TransportError(t))
    }

    client.logger.logRequest[R](path, arguments, result)
  }
}
