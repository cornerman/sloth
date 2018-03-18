package test.sloth

import org.scalatest._
import scala.concurrent.Future
import scala.util.control.NonFatal
import sloth._
import cats.implicits._

import chameleon._
import chameleon.ext.boopickle._
import boopickle.Default._
import java.nio.ByteBuffer
// import chameleon.ext.circe._
// import io.circe._, io.circe.syntax._, io.circe.generic.auto._

object Pickling {
  type PickleType = ByteBuffer
  // type PickleType = String
}
import Pickling._

trait EmptyApi
object EmptyApi extends EmptyApi

//shared
trait Api[Result[_]] {
  def simple: Result[Int]
  def fun(a: Int): Result[Int]
  def fun2(a: Int, b: String): Result[Int]
  def multi(a: Int)(b: Int): Result[Int]
}

//server
object ApiImplFuture extends Api[Future] {
  def simple: Future[Int] = Future.successful(1)
  def fun(a: Int): Future[Int] = Future.successful(a)
  def fun2(a: Int, b: String): Future[Int] = Future.successful(a)
  def multi(a: Int)(b: Int): Future[Int] = Future.successful(a)
}
//or
case class ApiResult[T](event: String, result: Future[T])
object ApiImplResponse extends Api[ApiResult] {
  def simple: ApiResult[Int] = ApiResult("peter", Future.successful(1))
  def fun(a: Int): ApiResult[Int] = ApiResult("hans", Future.successful(a))
  def fun2(a: Int, b: String): ApiResult[Int] = ApiResult("hans", Future.successful(a))
  def multi(a: Int)(b: Int): ApiResult[Int] = ApiResult("hans", Future.successful(a + b))
}
//or
object TypeHelper { type ApiResultFun[T] = Int => ApiResult[T] }
import TypeHelper._
object ApiImplFunResponse extends Api[ApiResultFun] {
  def simple: ApiResultFun[Int] = i => ApiResult("peter", Future.successful(i))
  def fun(a: Int): ApiResultFun[Int] = i => ApiResult("hans", Future.successful(a + i))
  def fun2(a: Int, b: String): ApiResultFun[Int] = i => ApiResult("hans", Future.successful(a + i))
  def multi(a: Int)(b: Int): ApiResultFun[Int] = i => ApiResult("hans", Future.successful(a + b + i))
}

class SlothSpec extends AsyncFreeSpec with MustMatchers {
  implicit val apiResultFunctor = cats.derive.functor[ApiResult]
  implicit val apiResultFunFunctor = cats.derive.functor[ApiResultFun]

  "run simple" in {
    object Backend {
      val router = Router[PickleType, Future]
        .route[Api[Future]](ApiImplFuture)
        .route(EmptyApi)
    }

    object Frontend {
      object Transport extends RequestTransport[PickleType, Future] {
        override def apply(request: Request[PickleType]): Future[PickleType] =
          Backend.router(request).toEither match {
            case Right(result) => result
            case Left(err) => Future.failed(new Exception(err.toString))
          }
      }

      val client = Client[PickleType, Future, ClientException](Transport)
      val api = client.wire[Api[Future]]
      val emptyApi = client.wire[EmptyApi]
    }

    Frontend.api.fun(1).map(_ mustEqual 1)
  }

 "run different result types" in {
    import cats.data.EitherT

    sealed trait ApiError
    case class SlothClientError(failure: ClientFailure) extends ApiError
    case class SlothServerError(failure: ServerFailure) extends ApiError
    case class UnexpectedError(msg: String) extends ApiError

    implicit def clientFailureConvert = new ClientFailureConvert[ApiError] {
      def convert(failure: ClientFailure) = SlothClientError(failure)
    }

    type ClientResult[T] = EitherT[Future, ApiError, T]

    object Backend {
      val router = Router[PickleType, ApiResult]
        .route[Api[ApiResult]](ApiImplResponse)
    }

    object Frontend {
      object Transport extends RequestTransport[PickleType, ClientResult] {
        override def apply(request: Request[PickleType]): ClientResult[PickleType] = EitherT(
          Backend.router(request).toEither match {
            case Right(ApiResult(event@_, result)) =>
              result.map(Right(_)).recover { case NonFatal(t) => Left(UnexpectedError(t.getMessage)) }
            case Left(err) => Future.successful(Left(SlothServerError(err)))
          })
      }

      val client = Client[PickleType, ClientResult, ApiError](Transport)
      val api = client.wire[Api[ClientResult]]
    }

    Frontend.api.fun2(1, "AAAA")
    Frontend.api.multi(11)(3)
    Frontend.api.fun(1).value.map(_.right.get mustEqual 1)
  }

 "run different result types with fun" in {

    object Backend {
      val router = Router[PickleType, ApiResultFun]
        .route[Api[ApiResultFun]](ApiImplFunResponse)
    }

    object Frontend {
      object Transport extends RequestTransport[PickleType, Future] {
        override def apply(request: Request[PickleType]): Future[PickleType] =
          Backend.router(request).toEither.fold(err => Future.failed(new Exception(err.toString)), _(10).result)
      }

      val client = Client[PickleType, Future, ClientException](Transport)
      val api = client.wire[Api[Future]]
    }

    Frontend.api.fun(1).map(_ mustEqual 11)
  }
}
