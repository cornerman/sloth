package sloth

case class RequestPath(apiName: String, methodName: String, meta: Vector[String] = Vector.empty)

case class Request[T](path: RequestPath, payload: T)
