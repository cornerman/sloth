package apitrait

import org.scalatest._
import scala.concurrent.{Future, ExecutionContext}

//shared
trait Api {
  def fun(a: Int): Future[Int]
}

//server
object ApiImpl extends Api {
  def fun(a: Int): Future[Int] = Future.successful(a)
}

import apitrait.core._

class CanMapFuture(implicit ec: ExecutionContext) extends CanMap[Future] {
  def apply[T, S](t: Future[T])(f: T => S): Future[S] = t.map(f)
}

class ApiTraitSpec extends AsyncFreeSpec with MustMatchers {
  val canMapFuture = new CanMapFuture

 "run boopickle" in {
    import boopickle.Default._
    import java.nio.ByteBuffer

    object BoopickleSerializer extends Serializer[Pickler, ByteBuffer] {
      override def serialize[T : Pickler](arg: T): ByteBuffer = Pickle.intoBytes(arg)
      override def deserialize[T : Pickler](arg: ByteBuffer): T = Unpickle[T].fromBytes(arg)
    }

    object Transport extends RequestTransport[Future, ByteBuffer] {
      override def apply(request: Request[ByteBuffer]): Future[ByteBuffer] = Backend.router(request)
    }

    object Backend {
      import apitrait.server._

      val server = new Server(BoopickleSerializer, canMapFuture)
      val router = server.route[Api](ApiImpl)
    }

    object Frontend {
      import apitrait.client._

      val client = new Client(BoopickleSerializer, canMapFuture, Transport)
      val api = client.wire[Api]
    }

    Frontend.api.fun(1).map(_ mustEqual 1)
  }

  "run circe" in {
    import io.circe._, io.circe.generic.auto._, io.circe.parser._, io.circe.syntax._, io.circe.shapes._

    class EncodeDecode[T](implicit val encoder: Encoder[T], val decoder: Decoder[T])
    implicit def EncoderWithDecoder[T : Encoder : Decoder]: EncodeDecode[T] = new EncodeDecode[T]

    object CirceSerializer extends Serializer[EncodeDecode, String] {
      override def serialize[T](arg: T)(implicit ed: EncodeDecode[T]): String = arg.asJson(ed.encoder).noSpaces
      override def deserialize[T](arg: String)(implicit ed: EncodeDecode[T]): T = decode[T](arg)(ed.decoder).right.get
    }

    object Transport extends RequestTransport[Future, String] {
      override def apply(request: Request[String]): Future[String] = Backend.router(request)
    }

    object Backend {
      import apitrait.server._

      val server = new Server(CirceSerializer, canMapFuture)
      val router = server.route[Api](ApiImpl)
    }

    object Frontend {
      import apitrait.client._

      val client = new Client(CirceSerializer, canMapFuture, Transport)
      val api = client.wire[Api]
    }

    Frontend.api.fun(1).map(_ mustEqual 1)
  }
}
