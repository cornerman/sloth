package sloth

import sloth.core._
import sloth.client.RequestTransport

import _root_.mycelium.client._

import scala.concurrent.{ExecutionContext, Future}

package object mycelium {

  implicit class TransportableWebsocketClient[PickleType, ErrorType](
    client: WebsocketClient[_, PickleType, _, ErrorType]) {

    def toTransport(behaviour: SendBehaviour)(implicit ec: ExecutionContext) = new RequestTransport[PickleType, Lambda[T => Future[Either[ErrorType, T]]]] {
      def apply(request: Request[PickleType]): Future[Either[ErrorType, PickleType]] = client.send(request.path, request.payload, behaviour)
    }

    def toTransport(behaviour: SendBehaviour, recover: ErrorType => Future[PickleType])(implicit ec: ExecutionContext) = new RequestTransport[PickleType, Future] {
      def apply(request: Request[PickleType]): Future[PickleType] = {
        client.send(request.path, request.payload, behaviour).flatMap {
          case Right(res) => Future.successful(res)
          case Left(err) => recover(err)
        }
      }
    }
  }
}
