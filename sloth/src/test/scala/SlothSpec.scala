package test

import scala.annotation.unused
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import sloth._
import cats.Functor
import cats.implicits._

import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.must.Matchers
import chameleon.ext.circe._
import io.circe.generic.auto._

trait EmptyApi
object EmptyApi extends EmptyApi

case class TwoInts(a:Int, b:Int)
trait SingleApi {
  def foo(a: Int, b: Int): Future[Int] = foo(TwoInts(a,b))
  def foo(ints: TwoInts): Future[Int]
  // should not compile because of wrong return type
  // def bum(a: Int): Option[Int]
  // should not compile because of generic parameter
  // def bom[T](a: T): Future[Int]
  @PathName("kanone") // does not compile without PathName, because overloaded
  def foo: Future[Int]
}
object SingleApiImpl extends SingleApi {
  def foo: Future[Int] = Future.successful(13)
  def foo(ints: TwoInts): Future[Int] = Future.successful(ints.a + ints.b)

  def bum(@unused a: Int): Option[Int] = Some(1)
  def bom[T](@unused a: T): Future[Int] = Future.successful(1)
}

trait OneApi[F[_]] {
  def one(a: Int): F[String]
}
object OneApiImpl extends OneApi[* => Future[Unit]] {
  type Result[X] = X => Future[Unit]

  val messages = collection.mutable.ArrayBuffer[String]()

  def one(a: Int): Result[String] = str => Future.successful(messages += s"$str: $a")
}

//shared
trait ExtendedApi[Result[_]] {
  def simple: Result[Int]
  def simpleBracket(): Result[Int]
}
trait Api[Result[_]] extends ExtendedApi[Result] {
  def single(a: Int): Result[Int]
  def fun(a: Int, b: String = "drei"): Result[Int]
  def fun2(a: Int, b: String): Result[Int]
  def multi(a: Int)(b: Int): Result[Int]
  def multi2(a: Int, b: String)(c: Double): Result[Int]
}

//server
object ApiImplFuture extends Api[Future] {
  def simple: Future[Int] = Future.successful(1)
  def simpleBracket(): Future[Int] = Future.successful(1)
  def single(a: Int): Future[Int] = Future.successful(a)
  def fun(a: Int, b: String): Future[Int] = Future.successful(a)
  def fun2(a: Int, b: String): Future[Int] = Future.successful(a)
  def multi(a: Int)(b: Int): Future[Int] = Future.successful(a)
  def multi2(a: Int, b: String)(c: Double): Future[Int] = Future.successful(a + c.toInt)
}
//or
case class ApiResult[T](event: String, result: Future[T])
object ApiResult {
  implicit def functor(implicit ec: ExecutionContext): Functor[ApiResult] = new Functor[ApiResult] {
    def map[A,B](fa: ApiResult[A])(f: A => B): ApiResult[B] = fa.copy(result = fa.result.map(f))
  }
}
object ApiImplResponse extends Api[ApiResult] {
  def simple: ApiResult[Int] = ApiResult("peter", Future.successful(1))
  def simpleBracket(): ApiResult[Int] = ApiResult("peter", Future.successful(1))
  def single(a: Int): ApiResult[Int] = ApiResult("peter", Future.successful(a))
  def fun(a: Int, b: String): ApiResult[Int] = ApiResult("hans", Future.successful(a))
  def fun2(a: Int, b: String): ApiResult[Int] = ApiResult("hans", Future.successful(a))
  def multi(a: Int)(b: Int): ApiResult[Int] = ApiResult("hans", Future.successful(a + b))
  def multi2(a: Int, b: String)(c: Double): ApiResult[Int] = ApiResult("hans", Future.successful(a + c.toInt))
}
//or
object TypeHelper {
  type ApiResultFun[T] = Int => ApiResult[T]

  implicit def functor(implicit ec: ExecutionContext): Functor[ApiResultFun] = new Functor[ApiResultFun] {
    def map[A,B](fa: ApiResultFun[A])(f: A => B): ApiResultFun[B] = i => Functor[ApiResult].map(fa(i))(f)
  }
}
import TypeHelper._
object ApiImplFunResponse extends Api[ApiResultFun] {
  def simple: ApiResultFun[Int] = i => ApiResult("peter", Future.successful(i))
  def simpleBracket(): ApiResultFun[Int] = i => ApiResult("peter", Future.successful(i))
  def single(a: Int): ApiResultFun[Int] = _ => ApiResult("peter", Future.successful(a))
  def fun(a: Int, b: String): ApiResultFun[Int] = i => ApiResult("hans", Future.successful(a + i))
  def fun2(a: Int, b: String): ApiResultFun[Int] = i => ApiResult("hans", Future.successful(a + i))
  def multi(a: Int)(b: Int): ApiResultFun[Int] = i => ApiResult("hans", Future.successful(a + b + i))
  def multi2(a: Int, b: String)(c: Double): ApiResultFun[Int] = i => ApiResult("hans", Future.successful(a + c.toInt + i))
}

class SlothSpec extends AsyncFreeSpec with Matchers {

  "run simple" in {
    object Backend {
      val router = Router[PickleType, Future]
        .route[EmptyApi](EmptyApi)
        .route[Api[Future]](ApiImplFuture)
        .route[SingleApi](SingleApiImpl)
    }

    object Frontend {
      object Transport extends RequestTransport[PickleType, Future] {
        override def apply(request: Request[PickleType]): Future[PickleType] = {
          println("WOLF " + request)
          Backend.router(request) match {
            case Right(result) => result
            case Left(err) => Future.failed(new Exception(err.toString))
          }
        }
      }

      val client = Client[PickleType, Future](Transport)
      val api = client.wire[Api[Future]]
      val singleApi = client.wire[SingleApi]
      val emptyApi = client.wire[EmptyApi]
    }

    for {
      simple <- Frontend.api.simple
      _ = simple mustEqual 1
      simpleBracket <- Frontend.api.simpleBracket()
      _ = simpleBracket mustEqual 1
      single <- Frontend.api.single(1)
      _ = single mustEqual 1
      foo <- Frontend.singleApi.foo(1, 2)
      _ = foo mustEqual 3
      foo2 <- Frontend.singleApi.foo
      _ = foo2 mustEqual 13
      fun <- Frontend.api.fun(1)
      _ = fun mustEqual 1
    } yield succeed
  }

  "run different result types" in {
    import cats.data.EitherT

    sealed trait ApiError
    case class SlothClientError(failure: ClientFailure) extends ApiError
    case class SlothServerError(failure: ServerFailure) extends ApiError
    case class UnexpectedError(msg: String) extends ApiError

    implicit def clientFailureConvert: ClientFailureConvert[ApiError] = SlothClientError(_)

    type ClientResult[T] = EitherT[Future, ApiError, T]

    object Backend {
      val router = Router[PickleType, ApiResult]
        .route[Api[ApiResult]](ApiImplResponse)
    }

    object Frontend {
      object Transport extends RequestTransport[PickleType, ClientResult] {
        override def apply(request: Request[PickleType]): ClientResult[PickleType] = EitherT {
          println(request)
          Backend.router(request) match {
            case Right(ApiResult(event@_, result)) =>
              result.map(Right(_)).recover { case NonFatal(t) => Left(UnexpectedError(t.getMessage)) }
            case Left(err) => Future.successful(Left(SlothServerError(err)))
          }
        }
      }

      val client = Client[PickleType, ClientResult](Transport)
      val api = client.wire[Api[ClientResult]]
    }

    for {
      fun2 <- Frontend.api.fun2(1, "AAAA").value
      _ = fun2 mustEqual Right(1)
      multi <- Frontend.api.multi(11)(3).value
      _ = multi mustEqual Right(14)
      multi2 <- Frontend.api.multi2(1, "hallo")(3.0).value
      _ = multi2 mustEqual Right(4)
      fun <- Frontend.api.fun(1).value
      _ = fun mustEqual Right(1)
    } yield succeed
  }

  "run different result types with fun" in {

    object Backend {
      val router = Router[PickleType, ApiResultFun]
        .route[Api[ApiResultFun]](ApiImplFunResponse)
    }

    object Frontend {
      object Transport extends RequestTransport[PickleType, Future] {
        override def apply(request: Request[PickleType]): Future[PickleType] = {
          println(request)
          Backend.router(request).fold(err => Future.failed(new Exception(err.toString)), _(10).result)
        }
      }

      val client = Client[PickleType, Future](Transport)
      val api = client.wire[Api[Future]]
    }

    for {
      fun <- Frontend.api.fun(1)
      _ = fun mustEqual 11
    } yield succeed
  }

  "run contra" in {

    object Backend {
      val router = Router.contra[PickleType, OneApiImpl.Result]
        .route[OneApi[OneApiImpl.Result]](OneApiImpl)
    }

    object Frontend {
      object Transport extends RequestTransport[PickleType, OneApiImpl.Result] {
        override def apply(request: Request[PickleType]): OneApiImpl.Result[PickleType] = {
          println(request)
          Backend.router(request) match {
            case Right(result) => result
            case Left(err) => _ => Future.failed(new Exception(err.toString))
          }
        }
      }

      val client = Client.contra[PickleType, OneApiImpl.Result](Transport)
      val api = client.wire[OneApi[OneApiImpl.Result]]
    }

    for {
      _ <- Future.successful(1)
      _ <- Frontend.api.one(1)("hallo")
      _ = OneApiImpl.messages.toList mustEqual List("hallo: 1")
      _ <- Frontend.api.one(13)("gut")
      _ = OneApiImpl.messages.toList mustEqual List("hallo: 1", "gut: 13")
    } yield succeed
  }
}
