package sloth

import sloth.internal.{PlatformSpecificRouterCo, PlatformSpecificRouterContra}

import cats.Functor
import cats.implicits._
import cats.~>

trait Router[PickleType, Result[_]] {
  protected def apiMap: Router.ApiMap[PickleType, Result]

  def apply(request: Request[PickleType]): Either[ServerFailure, Result[PickleType]] =
    getFunction(request.path) match {
      case Some(function) => function(request.payload)
      case None => Left(ServerFailure.PathNotFound(request.path))
    }

  def getFunction(path: RequestPath): Option[PickleType => Either[ServerFailure, Result[PickleType]]] =
    apiMap.get(path)

  def orElse(value: Router.ApiMap[PickleType, Result]): Router[PickleType, Result]
}

class RouterCo[PickleType, Result[_]](private[sloth] val logger: LogHandler[Result], protected val apiMap: Router.ApiMap[PickleType, Result])(implicit
  private[sloth] val functor: Functor[Result]
  ) extends Router[PickleType, Result] with PlatformSpecificRouterCo[PickleType, Result] {

  def orElse(value: Router.ApiMap[PickleType, Result]): RouterCo[PickleType, Result] = new RouterCo(logger, apiMap ++ value)

  def mapResult[R[_]: Functor](f: Result ~> R, logger: LogHandler[R] = LogHandler.empty[R]): Router[PickleType, R] = new RouterCo(logger, apiMap.map { case (k, v) => (k, v.map(_.map(f.apply))) }.toMap)
}

class RouterContra[PickleType, Result[_]](private[sloth] val logger: LogHandler[Result], protected val apiMap: Router.ApiMap[PickleType, Result])(implicit
  private[sloth] val routerHandler: RouterContraHandler[Result]
  ) extends Router[PickleType, Result] with PlatformSpecificRouterContra[PickleType, Result] {

  def orElse(value: Router.ApiMap[PickleType, Result]): RouterContra[PickleType, Result] = new RouterContra(logger, apiMap ++ value)

  def mapResult[R[_]: RouterContraHandler](f: Result ~> R, logger: LogHandler[R] = LogHandler.empty[R]): Router[PickleType, R] = new RouterContra(logger, apiMap.map { case (k, v) => (k, v.map(_.map(f.apply))) }.toMap)
}

object Router {
  type ApiFunction[PickleType, Result[_]] = PickleType => Either[ServerFailure, Result[PickleType]]
  type ApiMap[PickleType, Result[_]] = Map[RequestPath, ApiFunction[PickleType, Result]]

  def apply[PickleType, Result[_]: Functor]: RouterCo[PickleType, Result] = apply(LogHandler.empty[Result])
  def apply[PickleType, Result[_]: Functor](logger: LogHandler[Result]): RouterCo[PickleType, Result] = new RouterCo[PickleType, Result](logger, Map.empty)

  def contra[PickleType, Result[_]: RouterContraHandler]: RouterContra[PickleType, Result] = contra(LogHandler.empty[Result])
  def contra[PickleType, Result[_]: RouterContraHandler](logger: LogHandler[Result]): RouterContra[PickleType, Result] = new RouterContra[PickleType, Result](logger, Map.empty)
}
