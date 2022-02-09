package sloth

trait LogHandler[Result[_]] {
  def logRequest[T](path: List[String], argumentObject: Any, result: Result[T]): Result[T]
}
object LogHandler {
  def empty[Result[_]]: LogHandler[Result] = new LogHandler[Result] {
    def logRequest[T](path: List[String], argumentObject: Any, result: Result[T]): Result[T] = result
  }
}
