package test.sloth

import org.scalatest._
import scala.concurrent.Future
import scala.util.control.NonFatal

//shared
trait Api[Result[_], T] {
  def fun(a: Int, s: T): Result[Int]
}

//server
object ApiImplFuture extends Api[Future, String] {
  def fun(a: Int, s: String): Future[Int] = Future.successful(a)
}
//or
case class ServerResult[T](event: String, result: Future[T])
object ApiImplResponse extends Api[ServerResult, String] {
  def fun(a: Int, s: String): ServerResult[Int] = ServerResult(s, Future.successful(a))
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
      val router = server.route[ApiT[Future]](ApiImplFuture)
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

    implicit object BoopickleSerializer extends Serializer[Pickler, Pickler, ByteBuffer] {
      override def serialize[T : Pickler](arg: T): ByteBuffer = Pickle.intoBytes(arg)
      override def deserialize[T : Pickler](arg: ByteBuffer): Either[Throwable, T] = util.Try(Unpickle[T].fromBytes(arg)).toEither
    }

    object Transport extends RequestTransport[ByteBuffer, Future] {
      override def apply(request: Request[ByteBuffer]): Future[ByteBuffer] = Backend.router(request).fold(Future.failed(_), identity)
    }

    object Backend {
      import sloth.server._

      val server = Server[Pickler, Pickler, ByteBuffer, Future]
      val router = server.route[ApiT[Future]](ApiImplFuture)
    }

    object Frontend {
      import sloth.client._

      val client = Client[Pickler, Pickler, ByteBuffer, Future](Transport)
      val api = client.wire[ApiT[Future]]
    }

    Frontend.api.fun(1, "hi").map(_ mustEqual 1)
  }

 "run different result types" in {
    import cats.Functor
    import cats.data.EitherT

    sealed trait ApiError
    implicit class SlothError(failure: SlothFailure) extends ApiError
    case class UnexpectedError(msg: String) extends ApiError

    type ClientResult[T] = EitherT[Future, ApiError, T]
    type Encoder[T] = DummyImplicit
    type Decoder[T] = DummyImplicit
    type PickleType = Any

    implicit def serverResponseFunctor = new Functor[ServerResult] {
      def map[A, B](fa: ServerResult[A])(f: A => B): ServerResult[B] = fa.copy(result = fa.result map f)
    }

    implicit object AnySerializer extends Serializer[Encoder, Decoder, PickleType] {
      override def serialize[T : Encoder](arg: T): Any = arg
      override def deserialize[T : Decoder](arg: Any): Either[Throwable, T] = arg match {
        case arg: T => Right(arg)
        case arg => Left(new Exception(s"Cannot deserialize: $arg"))
      }
    }

    object Transport extends RequestTransport[PickleType, ClientResult] {
      override def apply(request: Request[PickleType]): ClientResult[PickleType] = EitherT(
        Backend.router(request) match {
          case Left(err) =>
            Future.successful(Left(SlothError(err)))
          case Right(ServerResult(event, result)) =>
            println(s"event: $event")
            result
              .map(Right(_))
              .recover { case NonFatal(t) => Left(UnexpectedError(t.getMessage)) }
        }
      )
    }

    object Backend {
      import sloth.server._

      val server = Server[Encoder, Decoder, PickleType, ServerResult]
      val router = server.route[ApiT[ServerResult]](ApiImplResponse)
    }

    object Frontend {
      import sloth.client._

      val client = Client[Encoder, Decoder, PickleType, ClientResult, ApiError](Transport)
      val api = client.wire[ApiT[ClientResult]]
    }

    Frontend.api.fun(1, "hi").value.map(_.right.get mustEqual 1)
  }
}
