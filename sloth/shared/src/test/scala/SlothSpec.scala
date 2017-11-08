package test.sloth

import org.scalatest._
import scala.concurrent.Future
import scala.util.control.NonFatal
import sloth.core._
import cats.implicits._

//shared
trait Api[Result[_]] {
  def fun(a: Int): Result[Int]
}

//server
object ApiImplFuture extends Api[Future] {
  def fun(a: Int): Future[Int] = Future.successful(a)
}
//or
case class ServerResult[T](event: String, result: Future[T])
object ApiImplResponse extends Api[ServerResult] {
  def fun(a: Int): ServerResult[Int] = ServerResult("hans", Future.successful(a))
}
//or
object TypeHelper { type ServerFunResult[T] = Int => ServerResult[T] }
import TypeHelper._
object ApiImplFunResponse extends Api[ServerFunResult] {
  def fun(a: Int): ServerFunResult[Int] = i => ServerResult("hans", Future.successful(a + i))
}

class SlothSpec extends AsyncFreeSpec with MustMatchers {

  type PickleType = Any

  implicit def anyWriter[T]: Writer[T, PickleType] = new Writer[T, PickleType] {
    override def write(arg: T): PickleType = arg
  }
  implicit def anyReader[T]: Reader[T, PickleType] = new Reader[T, PickleType] {
    override def read(arg: PickleType): Either[Throwable, T] = Right(arg.asInstanceOf[T])
  }

  "run simple" in {
    object Backend {
      import sloth.server._

      val server = Server[PickleType, Future]
      val router = server.route[Api[Future]](ApiImplFuture)
    }

    object Frontend {
      import sloth.client._

      object Transport extends RequestTransport[PickleType, Future] {
        override def apply(request: Request[PickleType]): Future[PickleType] =
          Backend.router(request).fold(Future.failed(_), identity)
      }

      val client = Client[PickleType, Future](Transport)
      val api = client.wire[Api[Future]]
    }

    Frontend.api.fun(1).map(_ mustEqual 1)
  }

 "run different result types" in {
    import cats.data.EitherT
    import cats.derived.functor._

    sealed trait ApiError
    implicit class SlothClientError(msg: SlothClientFailure) extends ApiError
    case class SlothServerError(msg: SlothServerFailure) extends ApiError
    case class UnexpectedError(msg: String) extends ApiError

    type ClientResult[T] = EitherT[Future, ApiError, T]

    object Backend {
      import sloth.server._

      val server = Server[PickleType, ServerResult]
      val router = server.route[Api[ServerResult]](ApiImplResponse)
    }

    object Frontend {
      import sloth.client._

      object Transport extends RequestTransport[PickleType, ClientResult] {
        override def apply(request: Request[PickleType]): ClientResult[PickleType] = EitherT(
          Backend.router(request) match {
            case Left(err) => Future.successful(Left(SlothServerError(err)))
            case Right(ServerResult(event, result)) =>
              result.map(Right(_)).recover { case NonFatal(t) => Left(UnexpectedError(t.getMessage)) }
          })
      }

      val client = Client[PickleType, ClientResult, ApiError](Transport)
      val api = client.wire[Api[ClientResult]]
    }

    Frontend.api.fun(1).value.map(_.right.get mustEqual 1)
  }

 "run different result types with fun" in {
    import cats.derived.functor._

    object Backend {
      import sloth.server._

      val server = Server[PickleType, ServerFunResult]
      val router = server.route[Api[ServerFunResult]](ApiImplFunResponse)
    }

    object Frontend {
      import sloth.client._

      object Transport extends RequestTransport[PickleType, Future] {
        override def apply(request: Request[PickleType]): Future[PickleType] =
          Backend.router(request).fold(Future.failed(_), _(10).result)
      }

      val client = Client[PickleType, Future](Transport)
      val api = client.wire[Api[Future]]
    }

    Frontend.api.fun(1).map(_ mustEqual 11)
  }
}
