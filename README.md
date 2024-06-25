# sloth :sloth:
[![sloth Scala version support](https://index.scala-lang.org/cornerman/sloth/sloth/latest-by-scala-version.svg?platform=sjs1)](https://index.scala-lang.org/cornerman/sloth/sloth)
[![sloth Scala version support](https://index.scala-lang.org/cornerman/sloth/sloth/latest-by-scala-version.svg?platform=jvm)](https://index.scala-lang.org/cornerman/sloth/sloth)

Type safe RPC in scala (scala 2 and scala 3)

Sloth is essentially a pair of macros (server and client) which takes an API definition in the form of a scala trait and then generates code for routing in the server as well as generating an API implementation in the client.

This library is inspired by [autowire](https://github.com/lihaoyi/autowire). Some differences:
* No macro application on the call-site in the client (`.call()`), just one macro for creating an instance of an API trait
* Return types of Api traits are not restricted to `Future`. You can use any higher-kinded generic return types:
  - Server: `cats.Functor` (or `cats.data.Kleisli` with `cats.ApplicativeError`)
  - Client: `cats.MonadError` (or `cats.data.Kleisli` with `cats.ApplicativeError`)

## Get started

Get latest release:
```scala
libraryDependencies += "com.github.cornerman" %%% "sloth" % "0.7.3"
```

We additonally publish snapshot releases for every commit.

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
val result = router(Request[ByteBuffer](Method(apiName = "Api", methodName = "fun"), bytes))
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

val client = Client[PickleType, Future](Transport)
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
    def fun(a: Int): F[String]
}
```

#### Router

In your server, you can use any `cats.Functor` as `F`, for example:
```scala
type ServerResult[T] = User => T

trait Api[F[_]] {
    def fun(a: Int): F[String]
}

object ApiImpl extends Api[ServerResult] {
    def fun(a: Int): User => String = { user =>
        println(s"User: $user")
        s"Number: $a"
    }
}

val router = Router[ByteBuffer, ServerResult]
    .route[Api[ServerResult]](ApiImpl)
```

It is also possible to have a contravariant return type in your server. You can use `Kleisli` (or a plain function) with any `cats.ApplicativeError` that can capture a `Throwable` or `ServerFailure` (see `ServerFailureConvert` / `RouterContraHandler` for more customization):
```scala
type ServerResult[T] = T => Either[ServerFailure, Unit]

trait Api[F[_]] {
    def fun(a: Int): F[String]
}

object ApiImpl extends Api[ServerResult] {
    def fun(a: Int): String => Either[ServerFailure, Unit] = { string =>
        println(s"Argument: $a")
        println(s"Return: $string")
        Right(())
    }
}

val router = Router.contra[ByteBuffer, ServerResult]
    .route[Api[ServerResult]](ApiImpl)
```

#### Client

In your client, you can use any `cats.MonadError` that can capture a `Throwable` or `ClientFailure` (see `ClientFailureConvert` / `ClientHandler` for more customization):
```scala
type ClientResult[T] = Either[ClientFailure, T]

val client = Client[PickleType, ClientResult](Transport)
val api: Api = client.wire[Api[ClientResult]]

api.fun(1): Either[ClientFailure, String]
```

It is also possible to have a contravariant return type in your client. You can use `Kleisli` (or a plain function) with any `cats.ApplicativeError` that can capture a `Throwable` or `ClientFailure` (see `ClientFailureConvert` / `ClientContraHandler` for more customization):
```scala
type ClientResult[T] = T => Either[ClientFailure, Unit]

val client = Client.contra[PickleType, ClientResult](Transport)
val api: Api = client.wire[Api[ClientResult]]

api.fun(1): String => Either[ClientFailure, Unit]
```

### Multiple routes

It is possible to have multiple APIs routed through the same router:
```scala
val router = Router[ByteBuffer, Future]
    .route[Api](ApiImpl)
    .route[OtherApi](OtherApiImpl)
```

### Router result

The router in the server returns an `Either[ServerFailure, Result[PickleType]]`, as the request can either fail or return the serialized result:
```scala
router(request) match {
    case Right(result) => println(s"Success: $result")
    case Left(error) => println(s"Error: $error")
}
```

### Logging

For logging, you can define a `LogHandler`, which can log each request including the deserialized request and response.

Define it when creating the `Client`:
```scala
object MyLogHandler extends LogHandler[ClientResult[_]] {
  def logRequest[T](method: Method, argumentObject: Any, result: ClientResult[T]): ClientResult[T] = ???
}

val client = Client[PickleType, ClientResult](Transport, MyLogHandler)
```

Define it when creating the `Router`:
```scala
object MyLogHandler extends LogHandler[ServerResult[_]] {
  def logRequest[T](method: Method, argumentObject: Any, result: ServerResult[T]): ServerResult[T] = ???
}

val router = Router[PickleType, ServerResult](MyLogHandler)
```

### Method overloading

When overloading methods with different parameter lists, sloth cannot have uniquely identify the method (because it is referenced with the trait name and the method name). Here you will need to provide a custom name:
```scala
trait Api {
    def fun(i: Int): F[Int]
    @Name("funWithString")
    def fun(i: Int, s: String): F[Int]
}
```

### Serialization

For serialization, we make use of the typeclasses provided by [chameleon](https://github.com/cornerman/chameleon). You can use existing libraries like circe, upickle, scodec or boopickle out of the box or define a serializer yourself (see the project readme). So you need a `Serializer` and `Deserializer` for each type you are using in the method signature of your API methods.

In the above examples, we used the type `ByteBuffer` to select the serialization method. We get implicit serializers/deserializers for `ByteBuffer` through the import `chameleon.ext.boopickle._`. Or you can use circe by providing the type `Json` (or String) and importing `chameleon.ext.circe._`. There are more available in chameleon.

## How does it work

Sloth derives all information about an API from a scala trait. For example:
```scala
// @Name("apiName")
trait Api {
    // @Name("funName")
    def fun(a: Int, b: String)(c: Double): F[Int]
}
```

For each declared method in this trait (in this case `fun`):
* Calculate method method: `Method("Api", "fun")` (`Name` annotations on the trait or method are taken into account).
* Serialize the method parameters as a tuple: `(a, b, c)`.

### Server

When calling `router.route[Api](impl)`, a macro generates a function that maps a method method and the pickled arguments to a pickled result. This basically boils down to:

```scala
{ (method: sloth.Method) =>
  if (method.apiName = "Api") method.methodName match {
    case "fun" => Some({ payload =>
        // deserialize payload
        // call Api implementation impl with arguments
        // return serialized response
    })
    case _ => None
  } else None
}
```

### Client

When calling `client.wire[Api]`, a macro generates an instance of `Api` by implementing each method using the provided transport:

```scala
new Api {
    def fun(a: Int, b: String)(c: Double): F[Int] = {
        // serialize arguments
        // call RequestTransport transport with method method and arguments
        // return deserialized response
    }
}
```

## Integrations

### http4s

Use with:
```
libraryDependencies += "com.github.cornerman" %%% "sloth-http4s-server" % "0.7.3"
libraryDependencies += "com.github.cornerman" %%% "sloth-http4s-client" % "0.7.3"
```

On the server:
```scala
import sloth.Router
import sloth.ext.http4s.server.HttpRpcRoutes

// for usual rpc
val router = Router[String, IO]
val rpcRoutes: HttpRoutes[IO] = HttpRpcRoutes[String, IO](router)

// for server sent event over rpc
val router = Router[String, fs2.Stream[IO, *]]
val rpcRoutes: HttpRoutes[IO] = HttpRpcRoutes.eventStream[IO](router)
```

In the client:
```scala
import sloth.Client
import sloth.ext.http4s.client.HttpRpcTransport

// for usual rpc
val client = Client[String, IO](HttpRpcTransport[String, IO])
val api: MyApi[IO] = client.wire[MyApi[IO]]

// for server sent events over rpc
val client = Client[String, fs2.Stream[IO, *]](HttpRpcTransport.eventStream[IO])
val api: MyApi[fs2.Stream[IO, *]] = client.wire[MyApi[fs2.Stream[IO, *]]]

```

### fetch (browser)

This is useful when running in the browser, because it will have a smaller bundle-size then using the http4s client.

Use with:
```
libraryDependencies += "com.github.cornerman" %%% "sloth-jsdom-client" % "0.7.3"
```

In the client:
```scala
import sloth.Client
import sloth.ext.jsdom.client.HttpRpcTransport

// for usual rpc
val client = Client[String, IO](HttpRpcTransport[IO])
val api: MyApi[IO] = client.wire[MyApi[IO]]
```

## Experimental: Checksum for Apis

Currently scala-2 only.

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
* All public methods in an API trait need to return the same higher kinded result type.
* Your chosen serialization library needs to support serializing tuples, which are generated by the macro for the parameter lists of each method in the API trait. This is normally the case.
