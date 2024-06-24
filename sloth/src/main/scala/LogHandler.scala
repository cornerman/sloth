package sloth

trait LogHandler[Result[_]] {
  def logRequest[A, T](endpoint: Endpoint, argumentObject: A, result: Result[T]): Result[T]
}
object LogHandler {
  def empty[Result[_]]: LogHandler[Result] = new LogHandler[Result] {
    def logRequest[A, T](endpoint: Endpoint, argumentObject: A, result: Result[T]): Result[T] = result
  }
}
