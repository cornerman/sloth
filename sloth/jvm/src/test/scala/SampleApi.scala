package test.sloth.mycelium

import scala.concurrent.{Future, ExecutionContext}
import sloth.core._
import cats.implicits._

//shared
trait Api[Result[_]] {
  def fun(a: Int): Result[Int]
  @PathName("funWithDefault")
  def fun(a: Int, b: Int): Result[Int] = fun(a + b)
}

object TypeHelper {
  type Event = String
  type State = String

  case class ApiValue[T](result: T, events: List[Event])
  case class ApiResult[T](state: Future[State], value: Future[ApiValue[T]])
  type ApiResultFun[T] = Future[State] => ApiResult[T]

  sealed trait ApiError
  case class SlothError(msg: String) extends ApiError

  implicit val apiValueFunctor = cats.derive.functor[ApiValue]
  implicit def apiResultFunctor(implicit ec: ExecutionContext) = cats.derive.functor[ApiResult]
  implicit def apiResultFunFunctor(implicit ec: ExecutionContext) = cats.derive.functor[ApiResultFun]
}
import TypeHelper._
//server
object ApiImpl extends Api[ApiResultFun] {
  def fun(a: Int): ApiResultFun[Int] =
    state => ApiResult(state, Future.successful(ApiValue(a, Nil)))
}
object FutureApiImpl extends Api[Future] {
  def fun(a: Int): Future[Int] = Future.successful(a)
}
