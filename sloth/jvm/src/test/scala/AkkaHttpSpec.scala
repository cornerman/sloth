package test.sloth.mycelium

import org.scalatest._

import sloth.core._
import sloth.akkahttp._
import chameleon.ext.boopickle._
import akka.stream.ActorMaterializer
import akka.actor.ActorSystem
import boopickle.Default._
import java.nio.ByteBuffer
import cats.implicits._

import scala.concurrent.Future
import scala.util.{Success, Failure}

class AkkaHttpSpec extends AsyncFreeSpec with MustMatchers with BeforeAndAfterAll {

  implicit val system = ActorSystem("akkahttp")
  implicit val materializer = ActorMaterializer()

  val port = 9998

  override def afterAll(): Unit = {
    system.terminate()
    ()
  }

 "run" in {
    object Backend {
      import sloth.server._

      val server = Server[ByteBuffer, Future]
      val router = server.route[Api[Future]](FutureApiImpl)

      //TODO this test of actually running belong into mycelium project
      def run() = {
        import akka.http.scaladsl.server.RouteResult._
        import akka.http.scaladsl.server.Directives, Directives._
        import akka.http.scaladsl.Http

        val route = pathPrefix("route") {
          router
        }

        Http().bindAndHandle(route, interface = "0.0.0.0", port = port).onComplete {
          case Success(binding) => println(s"Server online at ${binding.localAddress}")
          case Failure(err) => println(s"Cannot start server: $err")
        }
      }
    }

    object Frontend {
      import sloth.client._

      val requestTransport: RequestTransport[ByteBuffer, Future] = RequestTransport.httpClientFuture[ByteBuffer](s"http://localhost:$port/route")
      val client = Client[ByteBuffer, Future, ClientException](requestTransport)

      val api = client.wire[Api[Future]]
    }

    Backend.run()

    for {
      fun <- Frontend.api.fun(1)
      fun2 <- Frontend.api.fun(1, 2)
    } yield {
      fun mustEqual 1
      fun2 mustEqual 3
    }
  }
}
