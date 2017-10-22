package apitrait

import org.scalatest._
import scala.concurrent.Future
import shapeless._

import apitrait.core._

trait Api {
  def fun(a: Int): Future[Int]
}

object ApiImpl extends Api {
  def fun(a: Int): Future[Int] = Future.successful(a)
}

trait MyPickler[+A]
object MyPickler extends {
  implicit def getMyPickler[T] = new MyPickler[T] {}

  def serialize[T : MyPickler](o: T): Any = o
  def deserialize[T : MyPickler](o: Any): T = o.asInstanceOf[T]
}

object Backend {
  import apitrait.server._
  import scala.concurrent.ExecutionContext.Implicits.global

  object Bridge extends ServerBridge[MyPickler, Future, Any] {
    override def serialize[T : MyPickler](f: Future[T]): Future[Any] = f.map(MyPickler.serialize _)
    override def deserialize[T : MyPickler](args: Any): T = MyPickler.deserialize[T](args)
  }

  val server = new Server(Bridge)
  val router = server.route[Api](ApiImpl)
}

object Frontend {
  import apitrait.client._
  import scala.concurrent.ExecutionContext.Implicits.global

  object Bridge extends ClientBridge[MyPickler, Future, Any] {
    override def serialize[T : MyPickler](arg: T): Any = MyPickler.serialize(arg)
    override def deserialize[T : MyPickler](arg: Future[Any]): Future[T] = arg.map(MyPickler.deserialize[T](_))
    override def call(request: Request[Any]): Future[Any] = Backend.router(request)
  }

  val client = new Client(Bridge)
  val api = client.wire[Api]
}

class ApiTraitSpec extends AsyncFreeSpec with MustMatchers {
  "run" in {
    Frontend.api.fun(1).map(_ mustEqual 1)
  }
}
