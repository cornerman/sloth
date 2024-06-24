package sloth.ext.jsdom.client

import cats.effect.Async
import cats.implicits._
import sloth.{Request, RequestTransport}
import org.scalajs.dom

import scala.scalajs.js
import scala.scalajs.js.JSConverters._

case class HttpRequestConfig(
  baseUri: String = "/",
  headers: Map[String, String] = Map.empty,
)

object HttpRpcTransport {
  def apply[F[_]: Async]: RequestTransport[String, F] = apply(HttpRequestConfig().pure[F])

  def apply[F[_]: Async](config: F[HttpRequestConfig]): RequestTransport[String, F] = new RequestTransport[String, F] {
    override def apply(request: Request[String]): F[String] = for {
      config <- config
      url = s"${config.baseUri}/${request.endpoint.apiName}/${request.endpoint.methodName}"
      requestArgs = new dom.RequestInit { headers = config.headers.toJSDictionary; method = dom.HttpMethod.POST; body = request.payload }
      result <- Async[F].fromThenable(Async[F].delay[js.Thenable[String]](dom.fetch(url, requestArgs).`then`[String](_.text())))
    } yield result
  }
}
