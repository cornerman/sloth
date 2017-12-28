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

object TypeHelper {
  type Event = String
  type PublishEvent = String
  type State = String

  case class ApiResult[T](state: Future[State], events: Future[Seq[Event]], result: Future[T])
  type ApiResultF[T] = Future[State] => ApiResult[T]

  sealed trait ApiError
  case class SlothError(msg: String) extends ApiError
  case class OtherError(msg: String) extends ApiError
  implicit class ApiException(error: ApiError) extends Exception(error.toString)
  implicit def SlothFailureIsApiException(failure: SlothClientFailure): ApiException = SlothError(failure.toString)
}
import TypeHelper._
//server
object ApiImpl extends Api[ApiResultF] {
  def fun(a: Int): ApiResultF[Int] =
    state => ApiResult(state, Future.successful(Seq.empty), Future.successful(a))
}

class MyceliumSpec extends AsyncFreeSpec with MustMatchers {

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  val port = 9999

 "run" in {
    object Backend {
      import sloth.server._

      val config = ServerConfig(bufferSize = 5, overflowStrategy = OverflowStrategy.fail)

      val server = Server[ByteBuffer, ApiResultF]
      val router = server.route[Api[ApiResultF]](ApiImpl)

      val handler = new RequestHandler[ByteBuffer, Event, PublishEvent, ApiError, State] {
        def onClientConnect(client: NotifiableClient[PublishEvent]): Reaction = Reaction(Future.successful("empty"), Future.successful(Seq.empty))
        def onClientDisconnect(client: ClientIdentity, state: Future[State]): Unit = {}
        def onRequest(client: ClientIdentity, state: Future[State], path: List[String], payload: ByteBuffer): Response = {
          router(Request(path, payload)) match {
            case Left(err) =>
              Response(Reaction(state, Future.successful(Seq.empty[Event])), Future.successful(Left(SlothError(err.toString))))
            case Right(fun) =>
              val res = fun(state)
              Response(Reaction(res.state, res.events), res.result.map(Right(_)))
          }
        }
        def onEvent(client: ClientIdentity, state: Future[State], event: PublishEvent): Reaction = ???
      }

      val mycelium = WebsocketServerFlow[ByteBuffer, Event, PublishEvent, ApiError, State](config, handler)

      //TODO this test of actually running belong into mycelium project
      def run() = {
        import akka.http.scaladsl.server.RouteResult._
        import akka.http.scaladsl.server.Directives._
        import akka.http.scaladsl.Http

        val route = (path("ws") & get) {
          handleWebSocketMessages(mycelium)
        }

        Http().bindAndHandle(route, interface = "0.0.0.0", port = port).onComplete {
          case Success(binding) => println(s"Server online at ${binding.localAddress}")
          case Failure(err) => println(s"Cannot start server: $err")
        }
      }
    }

    object Frontend {
      import sloth.client._

      val config = ClientConfig(requestTimeoutMillis = 5 * 1000)
      val akkaConfig = AkkaWebsocketConfig(bufferSize = 5, overflowStrategy = OverflowStrategy.fail)

      val handler = new IncidentHandler[Event] {
        def onConnect(reconnect: Boolean): Unit = {}
        def onEvents(events: Seq[Event]): Unit = {}
      }

      val mycelium = WebsocketClient[ByteBuffer, Event, ApiError](
        AkkaWebsocketConnection(akkaConfig), config, handler)
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
