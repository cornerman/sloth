package sloth.ext.http4s.client

import cats.effect.Concurrent
import cats.implicits._
import org.http4s.client.Client
import org.http4s.{EntityBody, EntityDecoder, EntityEncoder, Headers, HttpVersion, Method, Request, ServerSentEvent, Uri}
import fs2.Stream
import sloth.RequestTransport

case class HttpRequestConfig(
  baseUri: Uri = Uri(path = Uri.Path.Root),
  headers: Headers = Headers.empty,
  httpVersion: HttpVersion = HttpVersion.`HTTP/1.1`,
) {
  def toRequest[F[_]](requestPath: List[String], entityBody: EntityBody[F]): Request[F] = Request[F](
    method = Method.POST,
    uri = requestPath.foldLeft(baseUri)(_ / _),
    httpVersion = httpVersion,
    headers = headers,
    body = entityBody,
  )
}

object HttpRpcTransport {
  def apply[PickleType, F[_]: Concurrent](
     client: Client[F],
   )(implicit
     encoder: EntityEncoder[F, PickleType],
     decoder: EntityDecoder[F, PickleType]
   ): RequestTransport[PickleType, F] = apply(client, HttpRequestConfig().pure[F])

  def apply[PickleType, F[_]: Concurrent](
     client: Client[F],
     config: F[HttpRequestConfig]
   )(implicit
     encoder: EntityEncoder[F, PickleType],
     decoder: EntityDecoder[F, PickleType]
  ): RequestTransport[PickleType, F] = new sloth.RequestTransport[PickleType, F] {
    override def apply(request: sloth.Request[PickleType]): F[PickleType] = for {
      config <- config
      responseBody <- client.expect[PickleType](config.toRequest(request.path, encoder.toEntity(request.payload).body))
    } yield responseBody
  }

  def eventStream[F[_]: Concurrent](
    client: Client[F],
  ): RequestTransport[String, Stream[F, *]] = eventStream(client, HttpRequestConfig().pure[F])

  def eventStream[F[_]: Concurrent](
    client: Client[F],
    config: F[HttpRequestConfig]
  ): RequestTransport[String, Stream[F, *]] = new sloth.RequestTransport[String, Stream[F, *]] {
    override def apply(request: sloth.Request[String]): Stream[F, String] = for {
      config <- Stream.eval(config)
      response <- Stream.resource(client.run(config.toRequest(request.path, EntityEncoder[F, String].toEntity(request.payload).body)))
      event <- response.body.through(ServerSentEvent.decoder[F])
      data <- Stream.fromOption(event.data)
    } yield data
  }
}