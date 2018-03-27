package sloth

case class RequestPath(apiName: String, methodName: String)
case class Request[T](path: RequestPath, payload: T)

object Arguments {
  case object Empty
}
