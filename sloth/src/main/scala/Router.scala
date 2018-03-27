package sloth

import sloth.internal.RouterMacro

import cats.Functor
import cats.syntax.functor._

class Router[PickleType, Result[_]](apiMap: Router.Map[PickleType, Result]) {
  def apply(request: Request[PickleType]): RouterResult[PickleType, Result] = {
    val function = apiMap.get(request.path.apiName).flatMap(_.get(request.path.methodName))
    function.fold[RouterResult[PickleType, Result]](RouterResult.Failure(None, ServerFailure.PathNotFound(request.path))) { f =>
      f(request.payload)
    }
  }

  def route[T](value: T)(implicit functor: Functor[Result]): Router[PickleType, Result] = macro RouterMacro.impl[T, PickleType, Result]

  def orElse(name: String, value: Router.MapValue[PickleType, Result]): Router[PickleType, Result] = new Router(apiMap + (name -> value))
}
object Router {
  type MapValue[PickleType, Result[_]] = collection.Map[String, PickleType => RouterResult[PickleType, Result]]
  type Map[PickleType, Result[_]] = collection.Map[String, MapValue[PickleType, Result]]

  def apply[PickleType, Result[_]]: Router[PickleType, Result] = new Router[PickleType, Result](collection.mutable.HashMap.empty)
}

sealed trait RouterResult[PickleType, +Result[_]] {
  def toEither: Either[ServerFailure, Result[PickleType]]
}
object RouterResult {
  case class Value[PickleType](raw: Any, serialized: PickleType)

  case class Failure[PickleType](argumentObject: Option[Product], failure: ServerFailure) extends RouterResult[PickleType, Lambda[X => Nothing]] {
    def toEither = Left(failure)
  }
  case class Success[PickleType, Result[_] : Functor](argumentObject: Product, result: Result[Value[PickleType]]) extends RouterResult[PickleType, Result] {
    def toEither = Right(result.map(_.serialized))
  }
}
