package apitrait

import org.scalatest._
import scala.concurrent.Future
import shapeless._
import java.nio.ByteBuffer
import boopickle.Default._

import apitrait.core._

trait Api {
  def fun(a: Int): Future[Int]
}

object Backend {
  import apitrait.server._
  import scala.concurrent.ExecutionContext.Implicits.global

  object ApiImpl extends Api {
    def fun(a: Int): Future[Int] = Future.successful(a)
  }

  object Bridge extends ServerBridge[Pickler, Future, ByteBuffer] {
    override def serialize[T : Pickler](f: Future[T]): Future[ByteBuffer] = f.map(Pickle.intoBytes(_))
    override def deserialize[T : Pickler](args: ByteBuffer): T = Unpickle[T].fromBytes(args)
  }

  val server = new Server(Bridge)
  val router = server.route[Api](ApiImpl)
}

object Frontend {
  import apitrait.client._
  import scala.concurrent.ExecutionContext.Implicits.global

  object Bridge extends ClientBridge[Pickler, Future, ByteBuffer] {
    override def serialize[T : Pickler](arg: T): ByteBuffer = Pickle.intoBytes(arg)
    override def deserialize[T : Pickler](arg: Future[ByteBuffer]): Future[T] = arg.map(Unpickle[T].fromBytes(_))
    override def call(request: Request[ByteBuffer]): Future[ByteBuffer] = Backend.router(request)
  }

  val client = new Client(Bridge)
  val api = client.wire[Api]
}

class ApiTraitSpec extends AsyncFreeSpec with MustMatchers {
  "run" in {
    Frontend.api.fun(1).map(_ mustEqual 1)
  }
}
