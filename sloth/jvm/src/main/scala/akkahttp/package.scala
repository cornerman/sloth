package sloth

import sloth.core._
import sloth.client.RequestTransport
import sloth.server.Router

import akka.actor.ActorSystem
import akka.util.{ByteString, ByteStringBuilder}
import akka.http.scaladsl.marshalling._
import akka.http.scaladsl.unmarshalling._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.{ActorMaterializer, Materializer}
import cats.data.EitherT

import java.nio.ByteBuffer
import scala.concurrent.{Future, ExecutionContext}
import scala.util.{Success, Failure}

package object akkahttp {
  import akka.http.scaladsl.server.{RouteResult, RequestContext}
  import akka.http.scaladsl.server.Directives._

  implicit def RouterIsDslRouter[PickleType : FromRequestUnmarshaller : ToResponseMarshaller](router: Router[PickleType, Future]): RequestContext => Future[RouteResult] = {
    (path(Remaining) & post) { pathRest =>
      decodeRequest {
        val path = pathRest.split("/").toList
        entity(as[PickleType]) { entity =>
          router(Request(path, entity)).toEither match {
            case Right(result) => onComplete(result) {
              case Success(r) => complete(r)
              case Failure(e) => complete(StatusCodes.InternalServerError -> e.toString)
            }
              case Left(err) => complete(StatusCodes.BadRequest -> err.toString)
          }
        }
      }
    }
  }

  implicit val ByteBufferUnmarshaller: FromByteStringUnmarshaller[ByteBuffer] = new FromByteStringUnmarshaller[ByteBuffer] {
    def apply(value: ByteString)(implicit ec: ExecutionContext, materializer: Materializer): Future[java.nio.ByteBuffer] =
      Future.successful(value.asByteBuffer)
  }
  implicit val ByteBufferEntityUnmarshaller: FromEntityUnmarshaller[ByteBuffer] = Unmarshaller.byteStringUnmarshaller.andThen(ByteBufferUnmarshaller)
  implicit val ByteBufferEntityMarshaller: ToEntityMarshaller[ByteBuffer] = Marshaller.ByteStringMarshaller.compose(ByteString(_))

  implicit class HttpRequestTransport(val factory: RequestTransport.type)(implicit system: ActorSystem, materializer: ActorMaterializer) {
    private def sendRequest[PickleType, ErrorType](
      baseUri: String,
      failedRequestError: (String, StatusCode) => ErrorType
    )(request: Request[PickleType])(implicit
      unmarshaller: FromByteStringUnmarshaller[PickleType],
      marshaller: ToEntityMarshaller[PickleType]) = {
      import system.dispatcher

      val uri = (baseUri :: request.path).mkString("/")
      val entity = Marshal(request.payload).to[MessageEntity]
      entity.flatMap { entity =>
        Http()
          .singleRequest(HttpRequest(method = HttpMethods.POST, uri = uri, headers = Nil, entity = entity))
          .flatMap { response =>
            response.status match {
              case StatusCodes.OK =>
                response.entity.dataBytes.runFold(new ByteStringBuilder)(_ append _).flatMap { b =>
                  Unmarshal(b.result).to[PickleType].map(Right.apply)
                }
              case code =>
                response.discardEntityBytes()
                Future.successful(Left(failedRequestError(uri, code)))
            }
          }
      }
    }

    def httpClientEitherT[PickleType, ErrorType](
      baseUri: String,
      failedRequestError: (String, StatusCode) => ErrorType,
      recover: PartialFunction[Throwable, ErrorType] = PartialFunction.empty
    )(implicit
      unmarshaller: FromByteStringUnmarshaller[PickleType],
      marshaller: ToEntityMarshaller[PickleType]) = new RequestTransport[PickleType, EitherT[Future, ErrorType, ?]] {
      import system.dispatcher

      private val sender = sendRequest[PickleType, ErrorType](baseUri, failedRequestError) _
      def apply(request: Request[PickleType]): EitherT[Future, ErrorType, PickleType] = EitherT {
        sender(request).recover(recover andThen Left.apply)
      }
    }

    def httpClientFuture[PickleType](
      baseUri: String
    )(implicit
      unmarshaller: FromByteStringUnmarshaller[PickleType],
      marshaller: ToEntityMarshaller[PickleType]) = new RequestTransport[PickleType, Future] {
      import system.dispatcher

      private val sender = sendRequest[PickleType, Exception](baseUri, (r,c) => new Exception(s"Http request failed $r: $c")) _
      def apply(request: Request[PickleType]): Future[PickleType] = {
        sender(request).flatMap {
          case Right(res) => Future.successful(res)
          case Left(err) => Future.failed(err)
        }
      }
    }
  }
}
