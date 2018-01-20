package test.sloth.boopickle

import org.scalatest._
import scala.concurrent.Future

import sloth.core._
import sloth.boopickle._

import cats.implicits._
import boopickle.Default._, java.nio.ByteBuffer

//shared
trait Api {
  def fun(a: Int): Future[Int]
}

//server
object ApiImpl extends Api {
  def fun(a: Int): Future[Int] = Future.successful(a)
}

class BoopickleSpec extends AsyncFreeSpec with MustMatchers {

 "run" in {
    object Backend {
      import sloth.server._

      val server = Server[ByteBuffer, Future]
      val router = server.route[Api](ApiImpl)
    }

    object Frontend {
      import sloth.client._

      object Transport extends RequestTransport[ByteBuffer, Future] {
        override def apply(request: Request[ByteBuffer]): Future[ByteBuffer] = Backend.router(request).fold(Future.failed(_), identity)
      }

      val client = Client[ByteBuffer, Future](Transport)
      val api = client.wire[Api]
    }

    Frontend.api.fun(1).map(_ mustEqual 1)
  }
}
