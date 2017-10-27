package sloth

import org.scalatest._
import scala.concurrent.Future
import scala.util.control.NonFatal

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

      val server = Server[Encoder, Decoder, String, Future]
      val router = server.route[ApiT[Future]](ApiImpl)
    }

    object Frontend {
      import sloth.client._

      val client = Client[Encoder, Decoder, String, Future](Transport)
      val api = client.wire[ApiT[Future]]
    }

    Frontend.api.fun(1, "hi").map(_ mustEqual 1)
  }

 "run boopickle" in {
    import boopickle.Default._, java.nio.ByteBuffer
    import cats.data.EitherT

    sealed trait ApiError
    implicit class SlothError(failure: SlothFailure) extends ApiError
    case class UnexpectedError(msg: String) extends ApiError

    type Result[T] = EitherT[Future, ApiError, T]

    implicit object BoopickleSerializer extends Serializer[Pickler, Pickler, ByteBuffer] {
      override def serialize[T : Pickler](arg: T): ByteBuffer = Pickle.intoBytes(arg)
      override def deserialize[T : Pickler](arg: ByteBuffer): Either[Throwable, T] = util.Try(Unpickle[T].fromBytes(arg)).toEither
    }

    object Transport extends RequestTransport[ByteBuffer, Result] {
      override def apply(request: Request[ByteBuffer]): Result[ByteBuffer] = EitherT(
        Backend.router(request) match {
          case Left(err) =>
            Future.successful(Left(SlothError(err)))
          case Right(result) =>
            result
              .map(Right(_))
              .recover { case NonFatal(t) => Left(UnexpectedError(t.getMessage)) }
        }
      )
    }

    object Backend {
      import sloth.server._

      val server = Server[Pickler, Pickler, ByteBuffer, Future]
      val router = server.route[ApiT[Future]](ApiImpl)
    }

    object Frontend {
      import sloth.client._

      val client = Client[Pickler, Pickler, ByteBuffer, Result, ApiError](Transport)
      val api = client.wire[ApiT[Result]]
    }

    Frontend.api.fun(1, "hi").value.map(_.right.get mustEqual 1)
  }
}
