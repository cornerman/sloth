package sloth

case class Endpoint(apiName: String, methodName: String)

case class Request[T](endpoint: Endpoint, payload: T) {
  @deprecated("Use .endpoint instead", "0.8.0")
  def path: List[String] = List(endpoint.apiName, endpoint.methodName)
}
object Request {
  @deprecated("""Use Request(Endpoint("Api", "method"), payload) instead""", "0.8.0")
  def apply[T](path: List[String], payload: T): Request[T] = Request(endpointFromList(path), payload)

  private[sloth] def endpointFromList(path: List[String]) = path match {
    case List(traitName, methodName) => Endpoint(apiName = traitName, methodName = methodName)
    case _ => Endpoint("", "")
  }
}
