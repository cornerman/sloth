package apitrait

import org.scalatest._
import scala.concurrent.Future

import apitrait.core._

//shared
trait Api {
  def fun(a: Int): Future[Int]
}

//server
object ApiImpl extends Api {
  def fun(a: Int): Future[Int] = Future.successful(a)
}

class ApiTraitSpec extends AsyncFreeSpec with MustMatchers {
 "run boopickle" in {
    import boopickle.Default._
    import java.nio.ByteBuffer

    object Backend {
      import apitrait.server._

      object Bridge extends ServerBridge[Pickler, Future, ByteBuffer] {
        override def serialize[T : Pickler](arg: Future[T]): Future[ByteBuffer] = arg.map(Pickle.intoBytes(_))
        override def deserialize[T : Pickler](arg: ByteBuffer): T = Unpickle[T].fromBytes(arg)
      }

      val server = new Server(Bridge)
      val router = server.route[Api](ApiImpl)
    }

    object Frontend {
      import apitrait.client._

      object Bridge extends ClientBridge[Pickler, Future, ByteBuffer] {
        override def serialize[T : Pickler](arg: T): ByteBuffer = Pickle.intoBytes(arg)
        override def deserialize[T : Pickler](arg: Future[ByteBuffer]): Future[T] = arg.map(Unpickle[T].fromBytes(_))
        override def call(request: Request[ByteBuffer]): Future[ByteBuffer] = Backend.router(request)
      }

      val client = new Client(Bridge)
      val api = client.wire[Api]
    }

    Frontend.api.fun(1).map(_ mustEqual 1)
  }

  "run circe" in {
    import shapeless._
    import io.circe._, io.circe.generic.auto._, io.circe.parser._, io.circe.syntax._, io.circe.shapes._

    class Serializer[T](implicit val encoder: Encoder[T], val decoder: Decoder[T])
    implicit def EncoderDecoderIsSerializer[T : Encoder : Decoder]: Serializer[T] = new Serializer[T]

    object Backend {
      import apitrait.server._

      object Bridge extends ServerBridge[Serializer, Future, String] {
        override def serialize[T](arg: Future[T])(implicit s: Serializer[T]): Future[String] = arg.map(_.asJson(s.encoder).noSpaces)
        override def deserialize[T](arg: String)(implicit s: Serializer[T]): T = decode[T](arg)(s.decoder).right.get // handle errors? return option[T], but then what?
      }

      val server = new Server(Bridge)
      val router = server.route[Api](ApiImpl)
    }

    object Frontend {
      import apitrait.client._

      object Bridge extends ClientBridge[Serializer, Future, String] {
        override def serialize[T](arg: T)(implicit s: Serializer[T]): String = arg.asJson(s.encoder).noSpaces
        override def deserialize[T](arg: Future[String])(implicit s: Serializer[T]): Future[T] = arg.map(arg => decode[T](arg)(s.decoder).right.get)
        override def call(request: Request[String]): Future[String] = Backend.router(request)
      }

      val client = new Client(Bridge)
      val api = client.wire[Api]
    }

    Frontend.api.fun(1).map(_ mustEqual 1)
  }
}
