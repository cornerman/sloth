package apitrait.core

case class Request[T](path: List[String], payload: T)
