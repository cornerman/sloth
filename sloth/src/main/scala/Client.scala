package sloth

import sloth.internal.{PlatformSpecificClientCo, PlatformSpecificClientContra}

trait Client[PickleType, Result[_]]

class ClientCo[PickleType, Result[_]](
  private[sloth] val transport: RequestTransport[PickleType, Result],
  private[sloth] val logger: LogHandler[Result]
)(implicit private[sloth] val failureHandler: ClientHandler[Result])
extends Client[PickleType, Result] with PlatformSpecificClientCo[PickleType, Result]

class ClientContra[PickleType, Result[_]](
  private[sloth] val transport: RequestTransport[PickleType, Result],
  private[sloth] val logger: LogHandler[Result]
)(implicit private[sloth] val failureHandler: ClientContraHandler[Result])
extends Client[PickleType, Result] with PlatformSpecificClientContra[PickleType, Result]

object Client {
  def apply[PickleType, Result[_]](transport: RequestTransport[PickleType, Result], logger: LogHandler[Result] = LogHandler.empty[Result])(implicit failureHandler: ClientHandler[Result]) = new ClientCo[PickleType, Result](transport, logger)

  def contra[PickleType, Result[_]](transport: RequestTransport[PickleType, Result], logger: LogHandler[Result] = LogHandler.empty[Result])(implicit failureHandler: ClientContraHandler[Result]) = new ClientContra[PickleType, Result](transport, logger)
}

trait RequestTransport[PickleType, Result[_]] { transport =>
  def apply(request: Request[PickleType]): Result[PickleType]

  final def map[R[_]](f: Result[PickleType] => R[PickleType]): RequestTransport[PickleType, R] = new RequestTransport[PickleType, R] {
    def apply(request: Request[PickleType]): R[PickleType] = f(transport(request))
  }
}
object RequestTransport {
  def apply[PickleType, Result[_]](f: Request[PickleType] => Result[PickleType]) = new RequestTransport[PickleType, Result] {
    def apply(request: Request[PickleType]): Result[PickleType] = f(request)
  }
}
