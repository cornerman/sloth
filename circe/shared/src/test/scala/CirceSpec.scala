package test.sloth.circe

import org.scalatest._
import scala.concurrent.Future

import sloth.core._
import sloth.circe._

import cats.implicits._
import io.circe.shapes._

//shared
trait Api {
  def fun(a: Int): Future[Int]
}

//server
object ApiImpl extends Api {
  def fun(a: Int): Future[Int] = Future.successful(a)
}

class CirceSpec extends AsyncFreeSpec with MustMatchers {

  "run" in {
    object Transport extends RequestTransport[String, Future] {
      override def apply(request: Request[String]): Future[String] = Backend.router(request).fold(Future.failed(_), identity)
    }

    object Backend {
      import sloth.server._

      val server = Server[String, Future]
      val router = server.route[Api](ApiImpl)
    }

    object Frontend {
      import sloth.client._

      val client = Client[String, Future](Transport)
      val api = client.wire[Api]
    }

    Frontend.api.fun(1).map(_ mustEqual 1)
  }
}
