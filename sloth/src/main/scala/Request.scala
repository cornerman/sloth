package sloth

case class RequestPath(apiName: String, methodName: String, meta: Set[String] = Set.empty)

case class Request[T](path: RequestPath, payload: T)
