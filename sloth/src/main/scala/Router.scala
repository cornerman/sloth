package sloth

import sloth.internal.RouterMacro

import cats.{Functor, ~>}

import cats.syntax.functor._

class Router[PickleType, Result[_]](apiMap: Router.Map[PickleType, Result]) {
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

  def mapK[R[_]](f: Result ~> R): Router[PickleType, R] = new Router(apiMap.mapValues(_.mapValues(_ andThen (_.mapK(f)))))

  def orElse(name: String, value: Router.MapValue[PickleType, Result]): Router[PickleType, Result] = new Router(apiMap + (name -> value))
}
object Router {
  type MapValue[PickleType, Result[_]] = collection.Map[String, PickleType => RouterResult[PickleType, Result]]
  type Map[PickleType, Result[_]] = collection.Map[String, MapValue[PickleType, Result]]

  def apply[PickleType, Result[_]]: Router[PickleType, Result] = new Router[PickleType, Result](collection.mutable.HashMap.empty)
}

sealed trait RouterResult[PickleType, Result[_]] {
  def toEither(implicit functor: Functor[Result]): Either[ServerFailure, Result[PickleType]]
  def map[T](f: PickleType => T)(implicit functor: Functor[Result]): RouterResult[T, Result]
  def mapK[R[_]](f: Result ~> R): RouterResult[PickleType, R]
}
object RouterResult {
  case class Value[PickleType](raw: Any, serialized: PickleType) {
    def map[T](f: PickleType => T): Value[T] = copy(serialized = f(serialized))
  }

  case class Failure[PickleType, Result[_]](argumentObject: Option[Product], failure: ServerFailure) extends RouterResult[PickleType, Result] {
    def toEither(implicit functor: Functor[Result]) = Left(failure)
    def map[T](f: PickleType => T)(implicit functor: Functor[Result]): RouterResult[T, Result] = copy[T, Result]()
    def mapK[R[_]](f: Result ~> R) = copy[PickleType, R]()
  }
  case class Success[PickleType, Result[_]](argumentObject: Product, result: Result[Value[PickleType]]) extends RouterResult[PickleType, Result] {
    def toEither(implicit functor: Functor[Result]) = Right(result.map(_.serialized))
    def map[T](f: PickleType => T)(implicit functor: Functor[Result]): RouterResult[T, Result] = copy[T, Result](result = result.map(_.map(f)))
    def mapK[R[_]](f: Result ~> R) = copy[PickleType, R](result = f(result))
  }
}
