package test.sloth

import sloth._
import cats._
import chameleon._
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import chameleon.ext.circe._
import io.circe.generic.auto._

import Pickling._

case class Argument(value: Int)

class ImplsSpec extends AnyFreeSpec with Matchers {
  "server impl" - {
    import RouterResult._
    import sloth.internal.RouterImpl

    "works" in {
      val impl = new RouterImpl[PickleType, Id]

      val argument = Argument(1)
      val pickledInput = Serializer[Argument, PickleType].serialize(argument)
      val resultValue = "Argument(1)"
      val pickledResult = Serializer[String, PickleType].serialize(resultValue)
      val result = impl.execute[Argument, String](pickledInput)(_.toString)

      result mustEqual Success[PickleType, Id](argument, Value(resultValue, pickledResult))
    }

    "catch exception" in {
      val impl = new RouterImpl[PickleType, Id]

      val argument = Argument(1)
      val pickledInput = Serializer[Argument, PickleType].serialize(argument)
      val exception = new Exception("meh")
      val result = impl.execute[Argument, String](pickledInput)(_ => throw exception)

      result mustEqual Failure(Some(argument), ServerFailure.HandlerError(exception))
    }
  }

  "client impl" - {
    import sloth.internal.ClientImpl

    type EitherResult[T] = Either[ClientFailure, T]

    "works" in {
      val successTransport = RequestTransport[PickleType, EitherResult](request => Right(request.payload))
      val client = Client[PickleType, EitherResult](successTransport)
      val impl = new ClientImpl(client)

      val argument = Argument(1)
      val result = impl.execute[Argument, Argument]("api" :: "f" :: Nil, argument)

      result mustEqual Right(argument)
    }

    "catch exception" in {
      val exception = new Exception("meh")
      val failureTransport = RequestTransport[PickleType, EitherResult](_ => throw exception)
      val client = Client[PickleType, EitherResult](failureTransport)
      val impl = new ClientImpl(client)

      val argument = Argument(1)
      val result = impl.execute[Argument, Double]("api" :: "f" :: Nil, argument)

      result mustEqual Left(ClientFailure.TransportError(exception))
    }
  }

  "client contra impl" - {
    import sloth.internal.ClientContraImpl

    type EitherResult[T] = T => Either[ClientFailure, Unit]

    "works" in {
      val receivedRequests = collection.mutable.ArrayBuffer.empty[Argument]
      val receivedParameters = collection.mutable.ArrayBuffer.empty[Argument]
      val successTransport = RequestTransport[PickleType, EitherResult] { request => parameter =>
        receivedRequests += Deserializer[Argument, PickleType].deserialize(request.payload).toOption.get
        receivedParameters += Deserializer[Argument, PickleType].deserialize(parameter).toOption.get
        Right(())
      }
      val client = Client.contra[PickleType, EitherResult](successTransport)
      val impl = new ClientContraImpl(client)

      val argument = Argument(1)
      val resultf = impl.execute[Argument, Argument]("api" :: "f" :: Nil, argument)

      resultf(Argument(2)) mustEqual Right(())

      receivedRequests mustEqual List(Argument(1))
      receivedParameters mustEqual List(Argument(2))
    }

    "catch exception" in {
      val exception = new Exception("meh")
      val failureTransport = RequestTransport[PickleType, EitherResult](_ => throw exception)
      val client = Client.contra[PickleType, EitherResult](failureTransport)
      val impl = new ClientContraImpl(client)

      val argument = Argument(1)
      val resultf = impl.execute[Argument, Double]("api" :: "f" :: Nil, argument)

      resultf(2.0) mustEqual Left(ClientFailure.TransportError(exception))
    }
  }
}
