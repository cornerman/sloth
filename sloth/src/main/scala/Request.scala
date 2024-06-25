package sloth

case class Method(apiName: String, methodName: String)

case class Request[T](method: Method, payload: T) {
  @deprecated("Use .method instead", "0.8.0")
  def path: List[String] = List(method.apiName, method.methodName)
}
object Request {
  @deprecated("""Use Request(Method("Api", "method"), payload) instead""", "0.8.0")
  def apply[T](path: List[String], payload: T): Request[T] = Request(methodFromList(path), payload)

  private[sloth] def methodFromList(path: List[String]) = path match {
    case List(traitName, methodName) => Method(apiName = traitName, methodName = methodName)
    case _ => Method("", "")
  }
}
