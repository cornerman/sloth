package test.sloth.mycelium

import org.scalatest._
import scala.concurrent.Future

import sloth.core._
import sloth.boopickle._
import sloth.mycelium._
import mycelium.client._
import mycelium.server._

import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.actor.ActorSystem
import cats.implicits._
import cats.derived.functor._
import boopickle.Default._, java.nio.ByteBuffer
import scala.util.{Success, Failure}

//shared
trait Api[Result[_]] {
  def fun(a: Int): Result[Int]
}

case class ApiResult[T](state: Future[String], events: Future[Seq[String]], result: Future[T])
//server
object ApiImpl extends Api[ApiResult] {
  def fun(a: Int): ApiResult[Int] =
    ApiResult(Future.successful("state"), Future.successful(Seq.empty), Future.successful(a))
}

class MyceliumSpec extends AsyncFreeSpec with MustMatchers {

  implicit val system = ActorSystem()

  type Event = String
  type PublishEvent = String
  type State = String

  sealed trait ApiError
  case class SlothError(msg: String) extends ApiError
  case class OtherError(msg: String) extends ApiError
  implicit class ApiException(error: ApiError) extends Exception(error.toString)
  implicit def SlothFailureIsApiException(failure: SlothClientFailure): ApiException = SlothError(failure.toString)

  val port = 9999

 "run" in {
    object Backend {
      import sloth.server._

      val config = ServerConfig(
        ServerConfig.Flow(bufferSize = 5, overflowStrategy = OverflowStrategy.dropNew))

      val server = Server[ByteBuffer, ApiResult]
      val router = server.route[Api[ApiResult]](ApiImpl)

      val handler = new RequestHandler[ByteBuffer, Event, PublishEvent, ApiError, State] {
        def onClientConnect(client: NotifiableClient[PublishEvent]): State = "empty"
        def onClientDisconnect(client: ClientIdentity, state: Future[State]): Unit = {}
        def onRequest(client: ClientIdentity, state: Future[State], path: List[String], payload: ByteBuffer): Response = {
          //TODO: router accepts state and somehow pass state to a function?
          router(Request(path, payload)) match {
            case Left(err) => Response(Reaction(state, Future.successful(Seq.empty[Event])), Future.successful(Left(SlothError(err.toString))))
            case Right(res) => Response(Reaction(res.state, res.events), res.result.map(Right(_)))
          }
        }
        def onEvent(client: ClientIdentity, state: Future[State], event: PublishEvent): Reaction = ???
      }

      implicit val system = ActorSystem("server")
      implicit val materializer = ActorMaterializer()

      val mycelium = WebsocketServerFlow[ByteBuffer, ByteBuffer, Event, PublishEvent, ApiError, State](config, handler)

      //TODO this test of actually running belong into mycelium project
      def run() = {
        import akka.http.scaladsl.server.RouteResult
        import akka.http.scaladsl.server.Directives._
        import akka.http.scaladsl.Http

        val route = (path("ws") & get) {
          handleWebSocketMessages(mycelium)
        }

        Http().bindAndHandle(RouteResult.route2HandlerFlow(route), interface = "0.0.0.0", port = port).onComplete {
          case Success(binding) => println(s"Server online at ${binding.localAddress}")
          case Failure(err) => println(s"Cannot start server: $err")
        }
      }
    }

    object Frontend {
      import sloth.client._

      val config = ClientConfig(
        ClientConfig.Request(timeoutMillis = 5 * 1000))

      val handler = new IncidentHandler[Event] {
        def onConnect(reconnect: Boolean): Unit = {}
        def onEvents(events: Seq[Event]): Unit = {}
      }

      val mycelium = WebsocketClient[ByteBuffer, ByteBuffer, Event, ApiError](config, handler)
      val client = Client[ByteBuffer, Future, ApiException](mycelium)

      val api = client.wire[Api[Future]]

      def run() = {
        mycelium.run(s"ws://localhost:$port/ws")
      }
    }

    Backend.run()
    Frontend.run()

    Frontend.api.fun(1).map(_ mustEqual 1)
  }
}
