package test.sloth.mycelium

import org.scalatest._

import sloth.core._
import sloth.mycelium._
import chameleon.ext.boopickle._
import mycelium.client._
import mycelium.server._
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.actor.ActorSystem
import boopickle.Default._
import java.nio.ByteBuffer
import cats.implicits._

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Success, Failure}

import TypeHelper._

class MyceliumSpec extends AsyncFreeSpec with MustMatchers with BeforeAndAfterAll {

  implicit val system = ActorSystem("mycelium")
  implicit val materializer = ActorMaterializer()

  val port = 9999

  override def afterAll(): Unit = {
    system.terminate()
    ()
  }

 "run" in {
    object Backend {
      import sloth.server._

      val config = WebsocketServerConfig(bufferSize = 5, overflowStrategy = OverflowStrategy.fail)
      val server = Server[ByteBuffer, ApiResultFun]
      val router = server.route[Api[ApiResultFun]](ApiImpl)

      val handler = new SimpleRequestHandler[ByteBuffer, Event, ApiError, State] {
        def initialState = Future.successful("empty")
        def onRequest(state: Future[State], path: List[String], payload: ByteBuffer) = {
          router(Request(path, payload)).toEither match {
            case Right(fun) =>
              val res = fun(state)
              Response(res.state, res.value.map(v => ReturnValue(Right(v.result), v.events)))
            case Left(err) =>
              Response(state, Future.successful(ReturnValue(Left(SlothError(err.toString)))))
          }
        }
      }

      val mycelium = WebsocketServer(config, handler)

      //TODO this test of actually running belong into mycelium project
      def run() = {
        import akka.http.scaladsl.server.RouteResult._
        import akka.http.scaladsl.server.Directives._
        import akka.http.scaladsl.Http

        val route = (path("ws") & get) {
          handleWebSocketMessages(mycelium.flow())
        }

        Http().bindAndHandle(route, interface = "0.0.0.0", port = port).onComplete {
          case Success(binding) => println(s"Server online at ${binding.localAddress}")
          case Failure(err) => println(s"Cannot start server: $err")
        }
      }
    }

    object Frontend {
      import sloth.client._

      val akkaConfig = AkkaWebsocketConfig(bufferSize = 5, overflowStrategy = OverflowStrategy.fail)
      val mycelium = WebsocketClient[ByteBuffer, Event, ApiError](
        new AkkaWebsocketConnection(akkaConfig), WebsocketClientConfig(), new IncidentHandler[Event])
      val requestTransport = RequestTransport.websocketClientFuture(mycelium, SendType.WhenConnected, requestTimeout = 30 seconds)
      val client = Client[ByteBuffer, Future, ClientException](requestTransport)

      val api = client.wire[Api[Future]]

      def run() = {
        mycelium.run(s"ws://localhost:$port/ws")
      }
    }

    Backend.run()
    Frontend.run()

    for {
      fun <- Frontend.api.fun(1)
      fun2 <- Frontend.api.fun(1, 2)
    } yield {
      fun mustEqual 1
      fun2 mustEqual 3
    }
  }
}
