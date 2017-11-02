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
    val circe = Client[Encoder, Decoder, String]
  }

  implicit class CirceServer(server: Server.type) {
    val circe = Server[Encoder, Decoder, String]
  }
}
