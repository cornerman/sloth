package sloth.internal

import sloth._
import chameleon.{Serializer, Deserializer}

import scala.util.{Success, Failure, Try}

class RouterImpl[PickleType, Result[_]](router: RouterCo[PickleType, Result]) {
  def execute[T, R](path: List[String], arguments: PickleType)(call: T => Result[R])(implicit deserializer: Deserializer[T, PickleType], serializer: Serializer[R, PickleType]): Either[ServerFailure, Result[PickleType]] = {
    deserializer.deserialize(arguments) match {
      case Right(arguments) =>
        Try(call(arguments)) match {
          case Success(result) =>
            val routerResult = router.functor.map(router.logger.logRequest[T, R](path, arguments, result))(serializer.serialize)
            Right(routerResult)
          case Failure(err) => Left(ServerFailure.HandlerError(err))
        }
      case Left(err) => Left(ServerFailure.DeserializerError(err))
    }
  }
}

class RouterContraImpl[PickleType, Result[_]](router: RouterContra[PickleType, Result]) {
  def execute[T, R](path: List[String], arguments: PickleType)(call: T => Result[R])(implicit deserializerT: Deserializer[T, PickleType], deserializerR: Deserializer[R, PickleType]): Either[ServerFailure, Result[PickleType]] = {
    deserializerT.deserialize(arguments) match {
      case Right(arguments) =>
        Try(call(arguments)) match {
          case Success(result) =>
            val routerResult = router.routerHandler.eitherContramap[R, PickleType](router.logger.logRequest[T, R](path, arguments, result)) { serialized =>
                deserializerR.deserialize(serialized).left.map(ServerFailure.DeserializerError(_))
              }
            Right(routerResult)
          case Failure(err) => Left(ServerFailure.HandlerError(err))
        }
      case Left(err) => Left(ServerFailure.DeserializerError(err))
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

    client.logger.logRequest[T, R](path, arguments, result)
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

    client.logger.logRequest[T, R](path, arguments, result)
  }
}
