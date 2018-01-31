package test.sloth

import org.scalatest._

import sloth.core._
import cats._
import cats.implicits._

class ImplsSpec extends FreeSpec with MustMatchers {
  import TestSerializer._

  "server impl" - {
    import sloth.server._
    import sloth.internal.ServerImpl

    "works" in {
      val server = Server[PickleType, Id]
      val impl = new ServerImpl(server)

      val result = impl.execute[Int, String]("api" :: Nil, 1)(_.toString)

      result mustEqual Right("1")
    }

    "catch exception" in {
      val server = Server[PickleType, Id]
      val impl = new ServerImpl(server)

      val exception = new Exception("meh")
      val result = impl.execute[Int, String]("api" :: Nil, 1)(_ => throw exception)

      result mustEqual Left(ServerFailure.HandlerError(exception))
    }
  }

  "client impl" - {
    import sloth.client._
    import sloth.internal.ClientImpl

    type EitherResult[T] = Either[ClientFailure, T]

    "works" in {
      val successTransport = RequestTransport[PickleType, EitherResult](request => Right(request.payload))
      val client = Client[PickleType, EitherResult, ClientFailure](successTransport)
      val impl = new ClientImpl(client)

      val result = impl.execute[Int, String]("path" :: Nil, 1)

      result mustEqual Right(1)
    }

    "catch exception" in {
      val exception = new Exception("meh")
      val failureTransport = RequestTransport[PickleType, EitherResult](_ => throw exception)
      val client = Client[PickleType, EitherResult, ClientFailure](failureTransport)
      val impl = new ClientImpl(client)

      val result = impl.execute[Int, String]("path" :: Nil, 1)

      result mustEqual Left(ClientFailure.TransportError(exception))
    }
  }
}
