package test.sloth

import org.scalatest._
import scala.concurrent.Future
import scala.util.control.NonFatal
import sloth.core._
import cats.implicits._

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

  type Encoder[T] = DummyImplicit
  type Decoder[T] = DummyImplicit
  type PickleType = Any

  implicit object AnySerializer extends Serializer[Encoder, Decoder, PickleType] {
    override def serialize[T : Encoder](arg: T): Any = arg
    override def deserialize[T : Decoder](arg: Any): Either[Throwable, T] = Right(arg.asInstanceOf[T])
  }

  "run simple" in {
    object Transport extends RequestTransport[PickleType, Future] {
      override def apply(request: Request[PickleType]): Future[PickleType] = Backend.router(request).fold(Future.failed(_), identity)
    }

    object Backend {
      import sloth.server._

      val server = Server[Encoder, Decoder, PickleType, Future]
      val router = server.route[ApiT[Future]](ApiImplFuture)
    }

    object Frontend {
      import sloth.client._

      val client = Client[Encoder, Decoder, PickleType, Future](Transport)
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

    implicit def serverResponseFunctor = new Functor[ServerResult] {
      def map[A, B](fa: ServerResult[A])(f: A => B): ServerResult[B] = fa.copy(result = fa.result map f)
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
