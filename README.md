# sloth
[![Build Status](https://travis-ci.org/cornerman/sloth.svg?branch=master)](https://travis-ci.org/cornerman/sloth)

Typesafe RPC in scala

This library is inspired by [autowire](https://github.com/lihaoyi/autowire). Some differences:
* No macro application on the call-side in the client (`.call()`), just one macro for creating an instance of an API trait
* Return types not restricted to `Future`
* Higher-kinded generic return types for API traits (`cats.MonadError` in client, `cats.Functor` in server)
* Generates custom case classes for each function

Get via jitpack (add the following to your `build.sbt`):
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


