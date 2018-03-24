# sloth
[![Build Status](https://travis-ci.org/cornerman/sloth.svg?branch=master)](https://travis-ci.org/cornerman/sloth)

Typesafe RPC in scala

Sloth is essentially a pair of macros which takes an API definition in the form of a scala trait and then generates code for routing on the server as well as generating an API implementation in the client.

This library is inspired by [autowire](https://github.com/lihaoyi/autowire). Some differences:
* No macro application on the call-side in the client (`.call()`), just one macro for creating an instance of an API trait
* Return types not restricted to `Future`
* Higher-kinded generic return types for API traits (`cats.MonadError` in client, `cats.Functor` in server)
* Generates custom case classes for each function

## Get started

Get it via jitpack (add the following to your `build.sbt`):
```scala
resolvers += "jitpack" at "https://jitpack.io"
libraryDependencies += "com.github.cornerman.sloth" %%% "sloth" % "master-SNAPSHOT"
```

## Usage

Define a trait as your `Api`:
```scala
trait Api {
    def fun(a: Int): Future[Int]
}
```

### Server

Implement your `Api`:
```scala
object ApiImpl extends Api {
    def fun(a: Int): Future[Int] = Future.successful(a + 1)
}
```

Define a router with, e.g., [boopickle](https://github.com/suzaku-io/boopickle):
```scala
import sloth._
import boopickle.Default._
import chameleon.ext.boopickle._
import java.nio.ByteBuffer
import cats.implicits._

val router = Router[ByteBuffer, Future].route[Api](ApiImpl)
```

Use it to route requests to your Api implementation:
```scala
val result = router(Request[ByteBuffer]("Api" :: "fun" :: Nil, bytes))
```

### Client

Generate an implementation for `Api` on the client side:
```scala
import sloth._
import boopickle.Default._
import chameleon.ext.boopickle._
import java.nio.ByteBuffer
import cats.implicits._

object Transport extends RequestTransport[PickleType, Future] {
    // implement the transport layer. this example just calls the router directly.
    // in reality, the request would be send over a connection.
    override def apply(request: Request[PickleType]): Future[PickleType] =
        router(request).toEither match {
            case Right(result) => result
            case Left(err) => Future.failed(new Exception(err.toString))
        }
}

val client = Client[PickleType, Future, ClientException](Transport)
val api: Api = client.wire[Api]
```

Make requests to the server like normal method calls:
```scala
api.fun(1).foreach { num =>
  println(s"Got response: $num")
}
```

### Generic return type

Sometimes it can be useful to have a different return type on the server and client, you can do so by making your API generic:
```scala
trait Api[F[_]] {
    def fun(a: Int): F[Int]
}
```

In your server, you can use any `cats.Functor` as `F`, for example:
```scala
type ServerResult[T] = User => T

object ApiImpl extends Api[ServerResult] {
    def fun(a: Int): User => Int = { user =>
        println(s"User: $user")
        a + 1
    }
}

val router = Router[ByteBuffer, ServerResult].route[Api[ServerResult]](ApiImpl)
```

In your client, you can use any `cats.MonadError` that can capture a `ClientFailure` (see `ClientFailureConvert` for using your own failure type):
```scala
type ClientResult[T] = Either[ClientFailure, T]

val client = Client[PickleType, ClientResult, ClientFailure](Transport)
val api: Api = client.wire[Api[ClientResult]]
```

### Multiple routes

It is possible to have multiple APIs routed through the same router:
```scala
val router = Router[ByteBuffer, Future]
    .route[Api](ApiImpl)
    .route[OtherApi](OtherApiImpl)
```

### Router result

The router in the server returns a `RouterResult[PickleType, Result[_]]` which either returns a result or fails with a `ServerFailure`. Furthermore, it gives access to the deserialized request:
```scala
router(request) match {
    case RouterResult.Success(arguments, result) => println(s"Success (arguments: $arguments): $result")
    case RouterResult.Failure(arguments, error) => println(s"Error (arguments: $arguments): $error")
}
```

Or you can just convert the result to an `Either[ServerFailure, Result[PickleType]]`:
```scala
router(request).toEither match {
    case Right(result) => println(s"Success: $result")
    case Left(error) => println(s"Error: $error")
}
```

### Client logging

For logging, you can define a `LogHandler`, which can log each request including the deserialized request and response. Define it when creating the `Client`:
```scala
object MyLogHandler extends LogHandler[ClientResult] {
  def logRequest(path: List[String], argumentObject: Product, result: ClientResult[_]): Unit = ???
}

val client = Client[PickleType, ClientResult, ClientFailure](Transport, MyLogHandler)
```
