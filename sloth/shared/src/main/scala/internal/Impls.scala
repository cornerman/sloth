package sloth.internal

import sloth.core._
import sloth.client.Client
import sloth.server.Server

class ServerImpl[Encoder[_], Decoder[_], PickleType, Result[_]](server: Server[Encoder, Decoder, PickleType, Result]) {
  import server._

  def execute[T : Decoder, R : Encoder](arguments: PickleType)(call: T => Result[R]): Either[SlothFailure, Result[PickleType]] = {
    serializer.deserialize[T](arguments) match {
      case Left(err) => Left(SlothFailure.DeserializationError(err))
      case Right(args) => Right(functor.map(call(args))(x => serializer.serialize[R](x)))
    }
  }
}

class ClientImpl[Encoder[_], Decoder[_], PickleType, Result[_], ErrorType](client: Client[Encoder, Decoder, PickleType, Result, ErrorType]) {
  import client._

  def execute[T : Encoder, R : Decoder](path: List[String], arguments: T): Result[R] = {
    val params = serializer.serialize[T](arguments)
    val result = transport(Request[PickleType](path, params))
    monad.flatMap(result) { result =>
      serializer.deserialize[R](result) match {
        case Left(err) => monad.raiseError(SlothFailure.DeserializationError(err))
        case Right(result) => monad.pure[R](result)
      }
    }
  }
}
