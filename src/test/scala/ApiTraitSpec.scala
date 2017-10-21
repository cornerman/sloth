package apitrait

import org.scalatest._
import scala.concurrent.Future
import shapeless._

trait Api {
  def fun(a: Int): Future[Int]
}

object ApiImpl extends Api {
  def fun(a: Int): Future[Int] = Future.successful(a)
}

trait MyPickler[+A]
object MyPickler {
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
  import apitrait.core._
  import scala.concurrent.ExecutionContext.Implicits.global

  object Bridge extends ClientBridge[MyPickler, Future, Any] {
    override def call[T : MyPickler, R](path: List[String], arguments: T): Future[R] = {
      val pickled = MyPickler.serialize(arguments)
      val request = Request[Any](path, pickled)
      val result = Backend.router(request)
      result.map(MyPickler.deserialize[R](_))
    }
  }

  val client = new Client(Bridge)
  val api = client.wired[Api]
}

class ApiTraitSpec extends AsyncFreeSpec with MustMatchers {
  "run" in {
    Frontend.api.fun(1).map(_ mustEqual 1)
  }
}
