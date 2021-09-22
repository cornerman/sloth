package sloth

case class Request[T](path: List[String], payload: T)
