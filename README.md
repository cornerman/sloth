# sloth
[![Build Status](https://travis-ci.org/cornerman/sloth.svg?branch=master)](https://travis-ci.org/cornerman/sloth)

Type safe RPC in scala

Sloth is essentially a pair of macros (server and client) which takes an API definition in the form of a scala trait and then generates code for routing in the server as well as generating an API implementation in the client.

This library is inspired by [autowire](https://github.com/lihaoyi/autowire). Some differences:
* No macro application on the call-site in the client (`.call()`), just one macro for creating an instance of an API trait
* Return types of Api traits are not restricted to `Future`. You can use any higher-kinded generic return types (`cats.MonadError` in client, `cats.Functor` in server)
* Generates custom case classes for each function (for serializing the parameter lists)

## Get started

Get it via jitpack (add the following to your `build.sbt`):
```scala
resolvers += "jitpack" at "https://jitpack.io"
libraryDependencies += "com.github.cornerman.sloth" %%% "sloth" % "master-SNAPSHOT"
```

## Example usage

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

Define a router where we can use, e.g., [boopickle](https://github.com/suzaku-io/boopickle) for serializing the arguments and result of a method:
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
// Now result contains the serialized Int result returned by the method ApiImpl.fun
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
    // in reality, the request would be sent over a connection.
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

## Additional features

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

val router = Router[ByteBuffer, ServerResult]
    .route[Api[ServerResult]](ApiImpl)
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

### Mixing result types

You might want to mix different result types in one Api trait, for example:
```scala
trait Api {
    def single: Future[Int]
    def stream: Observable[Int]
}
```

A `Router` and a `RequestTransport` need one concrete higher-kinded type to work on. Here, you need to decide for a type that can hold both, a future and an observable. You can do this by defining an implicit conversion `ResultMapping`:
```scala
implicit val futureToObservable: ResultMapping[Future, Observable] = new ResultMapping[Future, Observable] {
    def apply[T](result: Future[T]): Observable[T] = Observable.fromFuture(result)
}
implicit val observableToFuture: ResultMapping[Observable, Future] = new ResultMapping[Observable, Future] {
    def apply[T](result: Observable[T]): Future[T] = result.lastL.runAsync
}
```

Then you can define your `Router[PickleType, Observable]` and `Client[PickleType, Observable]` as usual.


### Client logging

For logging, you can define a `LogHandler`, which can log each request including the deserialized request and response. Define it when creating the `Client`:
```scala
object MyLogHandler extends LogHandler {
  def logRequest[Result[_], ErrorType](path: List[String], argumentObject: Product, result: Result[_])(implicit monad: MonadError[Result, _ >: ErrorType]): Unit = ()
}

val client = Client[PickleType, ClientResult, ClientFailure](Transport, MyLogHandler)
```

### Method overloading

When overloading methods with different parameter lists, sloth does not have a unique path (because it is derived from the trait name and the method name). Here you will need to provide your own path name:
```scala
trait Api {
    def fun(i: Int): F[Int]
    @PathName("funWithString")
    def fun(i: Int, s: String): F[Int]
}
```

### Serialization

For serialization, we make use of the typeclasses provided by [chameleon](https://github.com/cornerman/chameleon). You can use existing libraries like circe, upickle, scodec or boopickle out of the box or define a serializer yourself (see the project readme)

## How does it work

Sloth derives all information about an API from a scala trait. For example:
```scala
// @PathName("apiName")
trait Api {
    // @PathName("funName")
    def fun(a: Int, b: String)(c: Double): F[Int]
}
```

For each declared method in this trait (in this case `fun`):
* Calculate method path: `List("Api", "fun")` (`PathName` annotations on the trait or method are taken into account).
* Generate a case class representing the parameter lists: `case class _sloth_Api_fun(a: Int, b: String, c: Double)`.

### Server

When calling `router.route[Api](impl)`, a macro generates a function that maps a method path and a pickled case class to a pickled result. This basically boils down to:

```scala
HashMap("Api" -> HashMap("fun" -> { payload =>
    // deserialize payload
    // call Api implementation impl with arguments
    // return serialized response
}))
```

### Client

When calling `client.wire[Api]`, a macro generates an instance of `Api` by implementing each method using the provided transport:

```scala
new Api {
    def fun(a: Int, b: String)(c: Double): F[Int] = {
        // serialize arguments
        // call RequestTransport transport with method path and arguments
        // return deserialized response
    }
}
```

## Experimental: Checksum for Apis

In order to check the compatability of the client and server Api trait, you can calculate a checksum of your Api:

```scala
import sloth.ChecksumCalculator._

trait Api {
    def fun(s: String): Int
}

val checksum:Int = checksumOf[Api]

```

The checksum of an Api trait is calculated from its *Name* and its *methods* (including names and types of parameters and result type).

## Limitations

* Type parameters on methods in the API trait are not supported.
* All public methods in an API trait need to return a higher kinded type `F[T]`, where T is the value transported over the wire. It is also possible to return a non-generic type `T` directly, which is then automatically interpreted as `cats.Id[T]`.
* Your chosen serialization library needs to support serializing case classes, which are generated by the macro for the parameter lists of each method in the API trait.
