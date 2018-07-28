package test.sloth

import org.scalatest._

import sloth._
import cats._
import cats.implicits._
import chameleon._
import chameleon.ext.boopickle._
import boopickle.Default._
import java.nio.ByteBuffer

case class Argument(value: Int)

class ImplsSpec extends FreeSpec with MustMatchers {
  "server impl" - {
    import RouterResult._
    import sloth.internal.RouterImpl

    "works" in {
      val impl = new RouterImpl[ByteBuffer, Id]

      val argument = Argument(1)
      val pickledInput = Serializer[Argument, ByteBuffer].serialize(argument)
      val resultValue = "Argument(1)"
      val pickledResult = Serializer[String, ByteBuffer].serialize(resultValue)
      val result = impl.execute[Argument, String]("api" :: "f" :: Nil, pickledInput)(_.toString)

      result mustEqual Success[ByteBuffer, Id](argument, Value(resultValue, pickledResult))
    }

    "catch exception" in {
      val impl = new RouterImpl[ByteBuffer, Id]

      val argument = Argument(1)
      val pickledInput = Serializer[Argument, ByteBuffer].serialize(argument)
      val exception = new Exception("meh")
      val result = impl.execute[Argument, String]("api" :: "f" :: Nil, pickledInput)(_ => throw exception)

      result mustEqual Failure(Some(argument), ServerFailure.HandlerError(exception))
    }
  }

  "client impl" - {
    import sloth.internal.ClientImpl

    type EitherResult[T] = Either[ClientFailure, T]

    "works" in {
      val successTransport = RequestTransport[ByteBuffer, EitherResult](request => Right(request.payload))
      val client = Client.withError[ByteBuffer, EitherResult, ClientFailure](successTransport)
      val impl = new ClientImpl(client)

      val argument = Argument(1)
      val result = impl.execute[Argument, Argument]("api" :: "f" :: Nil, argument)

      result mustEqual Right(argument)
    }

    "catch exception" in {
      val exception = new Exception("meh")
      val failureTransport = RequestTransport[ByteBuffer, EitherResult](_ => throw exception)
      val client = Client.withError[ByteBuffer, EitherResult, ClientFailure](failureTransport)
      val impl = new ClientImpl(client)

      val argument = Argument(1)
      val result = impl.execute[Argument, Double]("api" :: "f" :: Nil, argument)

      result mustEqual Left(ClientFailure.TransportError(exception))
    }
  }
}
