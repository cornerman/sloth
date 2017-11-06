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

  type PickleType = Any

  implicit def anyWriter[T]: Writer[T, PickleType] = new Writer[T, PickleType] {
    override def write(arg: T): PickleType = arg
  }
  implicit def anyReader[T]: Reader[T, PickleType] = new Reader[T, PickleType] {
    override def read(arg: PickleType): Either[Throwable, T] = Right(arg.asInstanceOf[T])
  }

  "run simple" in {
    object Transport extends RequestTransport[PickleType, Future] {
      override def apply(request: Request[PickleType]): Future[PickleType] = Backend.router(request).fold(Future.failed(_), identity)
    }

    object Backend {
      import sloth.server._

      val server = Server[PickleType, Future]
      val router = server.route[ApiT[Future]](ApiImplFuture)
    }

    object Frontend {
      import sloth.client._

      val client = Client[PickleType, Future](Transport)
      val api = client.wire[ApiT[Future]]
    }

    Frontend.api.fun(1, "hi").map(_ mustEqual 1)
  }

 "run different result types" in {
    import cats.data.EitherT
    import cats.derived.functor._

    sealed trait ApiError
    implicit class SlothClientError(msg: SlothClientFailure) extends ApiError
    case class SlothServerError(msg: SlothServerFailure) extends ApiError
    case class UnexpectedError(msg: String) extends ApiError

    type ClientResult[T] = EitherT[Future, ApiError, T]

    object Transport extends RequestTransport[PickleType, ClientResult] {
      override def apply(request: Request[PickleType]): ClientResult[PickleType] = EitherT(
        Backend.router(request) match {
          case Left(err) =>
            Future.successful(Left(SlothServerError(err)))
          case Right(ServerResult(event, result)) =>
            result
              .map(Right(_))
              .recover { case NonFatal(t) => Left(UnexpectedError(t.getMessage)) }
        }
      )
    }

    object Backend {
      import sloth.server._

      val server = Server[PickleType, ServerResult]
      val router = server.route[ApiT[ServerResult]](ApiImplResponse)
    }

    object Frontend {
      import sloth.client._

      val client = Client[PickleType, ClientResult, ApiError](Transport)
      val api = client.wire[ApiT[ClientResult]]
    }

    Frontend.api.fun(1, "hi").value.map(_.right.get mustEqual 1)
  }
}
