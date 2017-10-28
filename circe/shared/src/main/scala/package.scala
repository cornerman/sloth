package sloth

import sloth.core._
import sloth.client.Client
import sloth.server.Server

import cats.{Functor, MonadError}
import io.circe._, io.circe.parser._, io.circe.syntax._, io.circe.shapes._

package object circe {
  implicit object CirceSerializer extends Serializer[Encoder, Decoder, String] {
    override def serialize[T : Encoder](arg: T): String = arg.asJson.noSpaces
    override def deserialize[T : Decoder](arg: String): Either[Throwable, T] = decode[T](arg)
  }

  implicit class CirceClient(client: Client.type) {
    def circe[Result[_]](
      transport: RequestTransport[String, Result])(implicit
      monad: MonadError[Result, _ >: SlothFailure]) = Client[Encoder, Decoder, String, Result, SlothFailure](transport)

    def circe[Result[_], ErrorType](
      transport: RequestTransport[String, Result])(implicit
      monad: MonadError[Result, _ >: ErrorType],
      failureIsError: SlothFailure => ErrorType) = Client[Encoder, Decoder, String, Result, ErrorType](transport)
  }

  implicit class CirceServer(server: Server.type) {
    def circe[Result[_]](implicit
      functor: Functor[Result]) = Server[Encoder, Decoder, String, Result]
  }
}
