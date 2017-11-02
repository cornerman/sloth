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
    val boopickle = Client[Pickler, Pickler, ByteBuffer]
  }

  implicit class BoopickleServer(server: Server.type) {
    val boopickle = Server[Pickler, Pickler, ByteBuffer]
  }
}
