# sloth
[![Build Status](https://travis-ci.org/cornerman/sloth.svg?branch=master)](https://travis-ci.org/cornerman/sloth)

Typesafe RPC in scala

This library is inspired by [autowire](https://github.com/lihaoyi/autowire). Some differences:
* No macro application on the call-side in the client (`.call()`), just one macro for creating an instance of an API trait
* Return types not restricted to `Future`
* Higher-kinded generic return types for API traits (`cats.MonadError` in client, `cats.Functor` in server)
* Generates custom case classes for each function

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
val bytes = Pickle.intoBytes(1)
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
    override def apply(request: Request[PickleType]): Future[PickleType] = ??? // implement the transport layer
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


