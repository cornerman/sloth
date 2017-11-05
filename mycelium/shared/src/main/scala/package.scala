package sloth

import sloth.core._

import _root_.mycelium.client._
import _root_.mycelium.core.{Reader => MyceliumReader, Writer => MyceliumWriter}

import scala.concurrent.{ExecutionContext, Future}

package object mycelium {
  implicit def ReaderIsReader[T, PickleType](implicit reader: Reader[T, PickleType]) = new MyceliumReader[T, PickleType] {
    def read(bm: PickleType): Either[Throwable,T] = reader.read(bm)
  }
  implicit def WriterIsWriter[T, PickleType](implicit writer: Writer[T, PickleType]) = new MyceliumWriter[T, PickleType] {
    def write(msg: T): PickleType = writer.write(msg)
  }

  //TODO: with other result type? e.g. Future[Either[Error, T]]
  implicit class MyceliumTransport[PickleType, Failure, ErrorType <: Throwable](
    client: WebsocketClient[_, PickleType, _, Failure])(implicit
    ec: ExecutionContext,
    failureIsError: Failure => ErrorType) extends RequestTransport[PickleType, Future] {

    def apply(request: Request[PickleType]): Future[PickleType] = {
      client.send(request.path, request.payload).map {
        case Left(err) => throw err
        case Right(res) => res
      }
    }
  }
}
