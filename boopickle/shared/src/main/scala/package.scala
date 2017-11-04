package sloth

import sloth.core._

import _root_.boopickle.Default._, java.nio.ByteBuffer

import scala.util.{Failure, Success, Try}

package object boopickle {
  implicit def boopickleWriter[T : Pickler]: Writer[T, ByteBuffer] = new Writer[T, ByteBuffer] {
    override def write(arg: T): ByteBuffer = Pickle.intoBytes(arg)
  }
  implicit def boopickleReader[T : Pickler]: Reader[T, ByteBuffer] = new Reader[T, ByteBuffer] {
    override def read(arg: ByteBuffer): Either[Throwable, T] =
      Try(Unpickle[T].fromBytes(arg)) match {
        case Success(arg) => Right(arg)
        case Failure(t) => Left(t)
      }
  }
}
