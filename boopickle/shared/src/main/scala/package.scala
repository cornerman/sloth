package sloth

import sloth.core._
import sloth.client.Client
import sloth.server.Server

import cats.{Functor, MonadError}
import _root_.boopickle.Default._, java.nio.ByteBuffer

import scala.util.{Failure, Success, Try}

package object boopickle {
  implicit object BoopickleSerializer extends Serializer[Pickler, Pickler, ByteBuffer] {
    override def serialize[T : Pickler](arg: T): ByteBuffer = Pickle.intoBytes(arg)
    override def deserialize[T : Pickler](arg: ByteBuffer): Either[Throwable, T] =
      Try(Unpickle[T].fromBytes(arg)) match {
        case Success(arg) => Right(arg)
        case Failure(t) => Left(t)
      }
  }

  implicit class BoopickleClient(client: Client.type) {
    def boopickle[Result[_]](
      transport: RequestTransport[ByteBuffer, Result])(implicit
      monad: MonadError[Result, _ >: SlothFailure]) = Client[Pickler, Pickler, ByteBuffer, Result, SlothFailure](transport)

    def boopickle[Result[_], ErrorType](
      transport: RequestTransport[ByteBuffer, Result])(implicit
      monad: MonadError[Result, _ >: ErrorType],
      failureIsError: SlothFailure => ErrorType) = Client[Pickler, Pickler, ByteBuffer, Result, ErrorType](transport)
  }

  implicit class BoopickleServer(server: Server.type) {
    def boopickle[Result[_]](implicit
      functor: Functor[Result]) = Server[Pickler, Pickler, ByteBuffer, Result]
  }
}
