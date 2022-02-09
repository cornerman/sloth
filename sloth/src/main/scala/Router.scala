package sloth

import sloth.internal.RouterMacro

import cats.Functor
import cats.syntax.functor._

class Router[PickleType, Result[_]](private[sloth] val logger: LogHandler[Result], apiMap: Router.Map[PickleType, Result]) {
  def apply(request: Request[PickleType]): RouterResult[PickleType, Result] = {
    def notFoundFailure: RouterResult[PickleType, Result] =
      RouterResult.Failure(None, ServerFailure.PathNotFound(request.path))

    request.path match {
      case apiName :: methodName :: Nil =>
        val function = apiMap.get(apiName).flatMap(_.get(methodName))
        function.fold[RouterResult[PickleType, Result]](notFoundFailure) { f =>
          f(request.payload)
        }
      case _ => notFoundFailure
    }
  }

  def route[T](value: T)(implicit functor: Functor[Result]): Router[PickleType, Result] = macro RouterMacro.impl[T, PickleType, Result]

  @annotation.nowarn("cat=deprecation") // the alternative .concat is not available in scala 2.12
  def orElse(name: String, value: Router.MapValue[PickleType, Result]): Router[PickleType, Result] = new Router(logger, apiMap + (name -> value))
}
object Router {
  type MapValue[PickleType, Result[_]] = collection.Map[String, PickleType => RouterResult[PickleType, Result]]
  type Map[PickleType, Result[_]] = collection.Map[String, MapValue[PickleType, Result]]

  def apply[PickleType, Result[_]]: Router[PickleType, Result] = apply(LogHandler.empty[Result])
  def apply[PickleType, Result[_]](logger: LogHandler[Result]): Router[PickleType, Result] = new Router[PickleType, Result](logger, collection.mutable.HashMap.empty)
}

sealed trait RouterResult[PickleType, +Result[_]] {
  def toEither: Either[ServerFailure, Result[PickleType]]
}
object RouterResult {
  case class Value[PickleType](raw: Any, serialized: PickleType)

  case class Failure[PickleType](argumentObject: Option[Any], failure: ServerFailure) extends RouterResult[PickleType, Lambda[X => Nothing]] {
    def toEither = Left(failure)
  }
  case class Success[PickleType, Result[_] : Functor](argumentObject: Any, result: Result[Value[PickleType]]) extends RouterResult[PickleType, Result] {
    def toEither = Right(result.map(_.serialized))
  }
}
