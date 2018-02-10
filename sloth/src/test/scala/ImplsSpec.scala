package test.sloth

import org.scalatest._

import sloth.core._
import cats._
import cats.implicits._
import shapeless._

class ImplsSpec extends FreeSpec with MustMatchers {
  import TestSerializer._

  "server impl" - {
    import sloth.server._
    import RouterResult._
    import sloth.internal.RouterImpl

    "works" in {
      val impl = new RouterImpl[PickleType, cats.Id]

      val result = impl.execute[(Int :: HNil) :: HNil, String]("api" :: Nil, (1 :: HNil) :: HNil)(_.runtimeList.head.asInstanceOf[HList].runtimeList.head.toString)

      result mustEqual Success[PickleType, cats.Id]((1 :: Nil) :: Nil, Value("1", "1"))
    }

    "catch exception" in {
      val impl = new RouterImpl[PickleType, cats.Id]

      val exception = new Exception("meh")
      val result = impl.execute[(Int :: HNil) :: HNil, String]("api" :: Nil, (1 :: HNil) :: HNil)(_ => throw exception)

      result mustEqual Failure((1 :: Nil) :: Nil, ServerFailure.HandlerError(exception))
    }
  }

  "client impl" - {
    import sloth.client._
    import sloth.internal.ClientImpl

    type EitherResult[T] = Either[ClientFailure, T]

    "works" in {
      val successTransport = RequestTransport[PickleType, EitherResult](request => Right(request.payload.asInstanceOf[HList].runtimeList.head.asInstanceOf[HList].runtimeList.head))
      val client = Client[PickleType, EitherResult, ClientFailure](successTransport)
      val impl = new ClientImpl(client)

      val result = impl.execute[(Int :: HNil) :: HNil, String]("path" :: Nil, (1 :: HNil) :: HNil)

      result mustEqual Right(1)
    }

    "catch exception" in {
      val exception = new Exception("meh")
      val failureTransport = RequestTransport[PickleType, EitherResult](_ => throw exception)
      val client = Client[PickleType, EitherResult, ClientFailure](failureTransport)
      val impl = new ClientImpl(client)

      val result = impl.execute[(Int :: HNil) :: HNil, String]("path" :: Nil, (1 :: HNil) :: HNil)

      result mustEqual Left(ClientFailure.TransportError(exception))
    }
  }
}
