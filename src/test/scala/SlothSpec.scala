package sloth

import org.scalatest._
import scala.concurrent.Future

//shared
trait Api[Result[_], T] {
  def fun(a: Int, s: T): Result[Int]
}

//server
object ApiImpl extends Api[Future, String] {
  def fun(a: Int, s: String): Future[Int] = Future.successful(a)
}

class SlothSpec extends AsyncFreeSpec with MustMatchers {
  type ApiT[T[_]] = Api[T, String]

  import sloth.core._
  import cats.implicits._

  "run circe" in {
    import io.circe._, io.circe.parser._, io.circe.syntax._, io.circe.shapes._

    implicit object CirceSerializer extends Serializer[Encoder, Decoder, String] {
      override def serialize[T : Encoder](arg: T): String = arg.asJson.noSpaces
      override def deserialize[T : Decoder](arg: String): Either[Throwable, T] = decode[T](arg)
    }

    object Transport extends RequestTransport[String, Future] {
      override def apply(request: Request[String]): Future[String] = Backend.router(request).fold(Future.failed(_), identity)
    }

    object Backend {
      import sloth.server._

      val server = new Server[Encoder, Decoder, String, Future]
      val router = server.route[ApiT[Future]](ApiImpl)
    }

    object Frontend {
      import sloth.client._

      val client = new Client[Encoder, Decoder, String, Future, Throwable](Transport)
      val api = client.wire[ApiT[Future]]
    }

    Frontend.api.fun(1, "hi").map(_ mustEqual 1)
  }

 "run boopickle" in {
    import boopickle.Default._, java.nio.ByteBuffer
    import cats.data.EitherT

    implicit object BoopickleSerializer extends Serializer[Pickler, Pickler, ByteBuffer] {
      override def serialize[T : Pickler](arg: T): ByteBuffer = Pickle.intoBytes(arg)
      override def deserialize[T : Pickler](arg: ByteBuffer): Either[Throwable, T] = util.Try(Unpickle[T].fromBytes(arg)).toEither
    }

    object Transport extends RequestTransport[ByteBuffer, EitherT[Future, SlothFailure, ?]] {
      override def apply(request: Request[ByteBuffer]): EitherT[Future, SlothFailure, ByteBuffer] =
        EitherT(Backend.router(request).fold[Future[Either[SlothFailure, ByteBuffer]]](err => Future.successful(Left(err)), _.map(Right(_))))
    }

    object Backend {
      import sloth.server._

      val server = new Server[Pickler, Pickler, ByteBuffer, Future]
      val router = server.route[ApiT[Future]](ApiImpl)
    }

    object Frontend {
      import sloth.client._

      type Result[T] = EitherT[Future, SlothFailure, T]
      val client = new Client[Pickler, Pickler, ByteBuffer, Result, SlothFailure](Transport)
      val api = client.wire[ApiT[Result]]
    }

    Frontend.api.fun(1, "hi").value.map(_.right.get mustEqual 1)
  }
}
