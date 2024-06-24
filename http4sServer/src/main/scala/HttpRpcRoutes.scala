package sloth.ext.http4s.server

import cats.data.OptionT
import cats.implicits._
import cats.effect.Concurrent
import org.http4s._
import org.http4s.dsl.Http4sDsl
import fs2.Stream
import sloth.{Endpoint, Router, ServerFailure}

object HttpRpcRoutes {

  def apply[PickleType: EntityDecoder[F, *]: EntityEncoder[F, *], F[_]: Concurrent](
    router: Router[PickleType, F],
    onError: PartialFunction[Throwable, F[Response[F]]] = PartialFunction.empty
  ): HttpRoutes[F] = withRequest[PickleType, F](_ => router, onError)

  def withRequest[PickleType: EntityDecoder[F, *]: EntityEncoder[F, *], F[_]: Concurrent](
    router: Request[F] => Router[PickleType, F],
    onError: PartialFunction[Throwable, F[Response[F]]] = PartialFunction.empty
  ): HttpRoutes[F] = {
    val dsl = Http4sDsl[F]
    import dsl._

    HttpRoutes[F] { request =>
        request.pathInfo.segments match {
          case Vector(apiName, methodName) =>
            val endpoint = Endpoint(apiName.decoded(), methodName.decoded())
            val result = router(request).get(endpoint).traverse { f =>
              request.as[PickleType].flatMap { payload =>
                f(payload) match {
                  case Left(error)     => serverFailureToResponse[F](dsl, onError)(error)
                  case Right(response) => Ok(response)
                }
              }
            }

            OptionT(result)
          case _ => OptionT.none
        }
    }
  }

  def eventStream[F[_]: Concurrent](
    router: Router[String, Stream[F, *]],
    onError: PartialFunction[Throwable, F[Response[F]]] = PartialFunction.empty
  ): HttpRoutes[F] = eventStreamWithRequest[F](_ => router, onError)

  def eventStreamWithRequest[F[_]: Concurrent](
    router: Request[F] => Router[String, Stream[F, *]],
    onError: PartialFunction[Throwable, F[Response[F]]] = PartialFunction.empty
  ): HttpRoutes[F] = {
    val dsl = Http4sDsl[F]
    import dsl._

    HttpRoutes[F] { request =>
      request.pathInfo.segments match {
        case Vector(apiName, methodName) =>
          val endpoint = Endpoint(apiName.decoded(), methodName.decoded())
          val result = router(request).get(endpoint).traverse { f =>
            request.as[String].flatMap { payload =>
              f(payload) match {
                case Left(error) => serverFailureToResponse[F](dsl, onError)(error)
                case Right(response) => Ok(response.map(r => ServerSentEvent(data = Some(r))))
              }
            }
          }

          OptionT(result)
        case _ => OptionT.none
      }
    }
  }

  private def serverFailureToResponse[F[_]: Concurrent](dsl: Http4sDsl[F], onError: PartialFunction[Throwable, F[Response[F]]])(failure: ServerFailure): F[Response[F]] = {
    import dsl._
    failure match {
      case ServerFailure.EndpointNotFound(_)    => NotFound()
      case ServerFailure.HandlerError(err)      => onError.lift(err).getOrElse(InternalServerError(err.getMessage))
      case ServerFailure.DeserializerError(err) => onError.lift(err).getOrElse(BadRequest(err.getMessage))
    }
  }
}
