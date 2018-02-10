package sloth

import sloth.core._
import sloth.client.RequestTransport

import _root_.mycelium.client._
import cats.data.EitherT

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration

package object mycelium {

  implicit class MyceliumRequestTransport(val factory: RequestTransport.type) extends AnyVal {

    def websocketClientEitherT[PickleType, ErrorType](client: WebsocketClient[PickleType, _, ErrorType], sendType: SendType, requestTimeout: FiniteDuration, recover: PartialFunction[Throwable, ErrorType] = PartialFunction.empty)(implicit ec: ExecutionContext) = new RequestTransport[PickleType, EitherT[Future, ErrorType, ?]] {
      def apply(request: Request[PickleType]): EitherT[Future, ErrorType, PickleType] = EitherT(client.send(request.path, request.payload, sendType, requestTimeout).recover(recover andThen Left.apply))
    }

    def websocketClientFuture[PickleType](client: WebsocketClient[PickleType, _, _], sendType: SendType, requestTimeout: FiniteDuration)(implicit ec: ExecutionContext) = new RequestTransport[PickleType, Future] {
      def apply(request: Request[PickleType]): Future[PickleType] = {
        client.send(request.path, request.payload, sendType, requestTimeout).flatMap {
          case Right(res) => Future.successful(res)
          case Left(err) => Future.failed(new Exception(s"Websocket request failed: $err"))
        }
      }
    }
  }
}
