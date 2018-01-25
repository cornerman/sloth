package sloth

import sloth.core._
import sloth.client.RequestTransport

import _root_.mycelium.client._

import scala.concurrent.{ExecutionContext, Future}

package object mycelium {

  //TODO: with other result type? e.g. Future[Either[Error, T]]
  implicit class MyceliumTransport[PickleType, Failure, ErrorType <: Throwable](
    client: WebsocketClient[_, PickleType, _, Failure])(implicit
    ec: ExecutionContext,
    failureIsError: Failure => ErrorType) extends RequestTransport[PickleType, Future] {

    def apply(request: Request[PickleType]): Future[PickleType] = {
      client.send(request.path, request.payload).map {
        case Right(res) => res
        case Left(err) => throw err
      }
    }
  }
}
