package sloth

import sloth.internal.{PlatformSpecificRouterCo, PlatformSpecificRouterContra}

import cats.Functor
import cats.implicits._
import cats.~>

trait Router[PickleType, Result[_]] {
  val get: Router.ApiMapping[PickleType, Result]

  def apply(request: Request[PickleType]): Either[ServerFailure, Result[PickleType]] =
    get(request.endpoint) match {
      case Some(function) => function(request.payload)
      case None => Left(ServerFailure.EndpointNotFound(request.endpoint))
    }

  @deprecated("Use get(endpoint) instead", "0.8.0")
  def getFunction(path: List[String]): Option[PickleType => Either[ServerFailure, Result[PickleType]]] = get(Request.endpointFromList(path))

  def orElse(collect: Router.ApiMapping[PickleType, Result]): Router[PickleType, Result]
}

class RouterCo[PickleType, Result[_]](private[sloth] val logger: LogHandler[Result], val get: Router.ApiMapping[PickleType, Result])(implicit
                                                                                                                                                       private[sloth] val functor: Functor[Result]
  ) extends Router[PickleType, Result] with PlatformSpecificRouterCo[PickleType, Result] {

  def orElse(collect: Router.ApiMapping[PickleType, Result]): RouterCo[PickleType, Result] = new RouterCo(logger, request => get(request).orElse(collect(request)))

  def mapResult[R[_]: Functor](f: Result ~> R, logger: LogHandler[R] = LogHandler.empty[R]): Router[PickleType, R] = new RouterCo(logger, request => get(request).map { case v => v.map(_.map(f.apply)) })
}

class RouterContra[PickleType, Result[_]](private[sloth] val logger: LogHandler[Result], val get: Router.ApiMapping[PickleType, Result])(implicit
                                                                                                                                                           private[sloth] val routerHandler: RouterContraHandler[Result]
  ) extends Router[PickleType, Result] with PlatformSpecificRouterContra[PickleType, Result] {

  def orElse(collect: Router.ApiMapping[PickleType, Result]): RouterContra[PickleType, Result] = new RouterContra(logger, request => get(request).orElse(collect(request)))

  def mapResult[R[_]: RouterContraHandler](f: Result ~> R, logger: LogHandler[R] = LogHandler.empty[R]): Router[PickleType, R] = new RouterContra(logger, request => get(request).map { case v => v.map(_.map(f.apply)) })
}

object Router {
  type ApiMapping[PickleType, Result[_]] = Endpoint => Option[PickleType => Either[ServerFailure, Result[PickleType]]]
  private val emptyApiMapping: Any => None.type = (_: Any) => None

  def apply[PickleType, Result[_]: Functor]: RouterCo[PickleType, Result] = apply(LogHandler.empty[Result])
  def apply[PickleType, Result[_]: Functor](logger: LogHandler[Result]): RouterCo[PickleType, Result] = new RouterCo[PickleType, Result](logger, emptyApiMapping)

  def contra[PickleType, Result[_]: RouterContraHandler]: RouterContra[PickleType, Result] = contra(LogHandler.empty[Result])
  def contra[PickleType, Result[_]: RouterContraHandler](logger: LogHandler[Result]): RouterContra[PickleType, Result] = new RouterContra[PickleType, Result](logger, emptyApiMapping)
}
