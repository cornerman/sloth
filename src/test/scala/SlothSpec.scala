package sloth

import org.scalatest._
import scala.concurrent.{Future, ExecutionContext}

//shared
trait Api[Result[_], T] {
  def fun(a: Int, s: T): Result[Int]
}

//server
object ApiImpl extends Api[Future, String] {
  def fun(a: Int, s: String): Future[Int] = Future.successful(a)
}

import sloth.core._

class CanMapFuture(implicit ec: ExecutionContext) extends CanMap[Future] {
  def apply[T, S](t: Future[T])(f: T => S): Future[S] = t.map(f)
}

class SlothSpec extends AsyncFreeSpec with MustMatchers {
  val canMapFuture = new CanMapFuture
  type ApiFuture = Api[Future, String]

 "run boopickle" in {
    import boopickle.Default._, java.nio.ByteBuffer

    object BoopickleSerializer extends Serializer[Pickler, Pickler, ByteBuffer] {
      override def serialize[T : Pickler](arg: T): ByteBuffer = Pickle.intoBytes(arg)
      override def deserialize[T : Pickler](arg: ByteBuffer): T = Unpickle[T].fromBytes(arg)
    }

    object Transport extends RequestTransport[Future, ByteBuffer] {
      override def apply(request: Request[ByteBuffer]): Future[ByteBuffer] = Backend.router(request)
    }

    object Backend {
      import sloth.server._

      val server = new Server(BoopickleSerializer, canMapFuture)
      val router = server.route[ApiFuture](ApiImpl)
    }

    object Frontend {
      import sloth.client._

      val client = new Client(BoopickleSerializer, canMapFuture, Transport)
      val api = client.wire[ApiFuture]
    }

    Frontend.api.fun(1, "hi").map(_ mustEqual 1)
  }

  "run circe" in {
    import io.circe._, io.circe.generic.auto._, io.circe.parser._, io.circe.syntax._, io.circe.shapes._

    object CirceSerializer extends Serializer[Encoder, Decoder, String] {
      override def serialize[T : Encoder](arg: T): String = arg.asJson.noSpaces
      override def deserialize[T : Decoder](arg: String): T = decode[T](arg).right.get
    }

    object Transport extends RequestTransport[Future, String] {
      override def apply(request: Request[String]): Future[String] = Backend.router(request)
    }

    object Backend {
      import sloth.server._

      val server = new Server(CirceSerializer, canMapFuture)
      val router = server.route[ApiFuture](ApiImpl)
    }

    object Frontend {
      import sloth.client._

      val client = new Client(CirceSerializer, canMapFuture, Transport)
      val api = client.wire[ApiFuture]
    }

    Frontend.api.fun(1, "hi").map(_ mustEqual 1)
  }
}
