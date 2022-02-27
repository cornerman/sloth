package sloth

import sloth.internal.RouterMacro

import cats.Functor

trait Router[PickleType, Result[_]] {
  protected def apiMap: Router.ApiMap[PickleType, Result]

  def apply(request: Request[PickleType]): Either[ServerFailure, Result[PickleType]] =
    getFunction(request.path) match {
      case Some(function) => function(request.payload)
      case None => Left(ServerFailure.PathNotFound(request.path))
    }

  def getFunction(path: List[String]): Option[PickleType => Either[ServerFailure, Result[PickleType]]] =
    path match {
      case apiName :: methodName :: Nil => apiMap.get(apiName).flatMap(_.get(methodName))
      case _ => None
    }
}

class RouterCo[PickleType, Result[_]](private[sloth] val logger: LogHandler[Result], protected val apiMap: Router.ApiMap[PickleType, Result])(implicit
  private[sloth] val functor: Functor[Result]
  ) extends Router[PickleType, Result] {

  def route[T](value: T): RouterCo[PickleType, Result] = macro RouterMacro.impl[T, PickleType, Result]

  def orElse(name: String, value: Router.ApiMapValue[PickleType, Result]): RouterCo[PickleType, Result] = new RouterCo(logger, apiMap + (name -> value))
}

class RouterContra[PickleType, Result[_]](private[sloth] val logger: LogHandler[Result], protected val apiMap: Router.ApiMap[PickleType, Result])(implicit
  private[sloth] val routerHandler: RouterContraHandler[Result]
  ) extends Router[PickleType, Result] {

  def route[T](value: T): RouterContra[PickleType, Result] = macro RouterMacro.implContra[T, PickleType, Result]

  def orElse(name: String, value: Router.ApiMapValue[PickleType, Result]): RouterContra[PickleType, Result] = new RouterContra(logger, apiMap + (name -> value))
}

object Router {
  type ApiMapValue[PickleType, Result[_]] = Map[String, PickleType => Either[ServerFailure, Result[PickleType]]]
  type ApiMap[PickleType, Result[_]] = Map[String, ApiMapValue[PickleType, Result]]

  def apply[PickleType, Result[_]: Functor]: RouterCo[PickleType, Result] = apply(LogHandler.empty[Result])
  def apply[PickleType, Result[_]: Functor](logger: LogHandler[Result]): RouterCo[PickleType, Result] = new RouterCo[PickleType, Result](logger, Map.empty)

  def contra[PickleType, Result[_]: RouterContraHandler]: RouterContra[PickleType, Result] = contra(LogHandler.empty[Result])
  def contra[PickleType, Result[_]: RouterContraHandler](logger: LogHandler[Result]): RouterContra[PickleType, Result] = new RouterContra[PickleType, Result](logger, Map.empty)
}
