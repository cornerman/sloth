package sloth

import cats.arrow.FunctionK

trait ResultMapping[From[_], To[_]] extends FunctionK[From, To] {
  def apply[T](result: From[T]): To[T]
}
object ResultMapping {
  implicit def identityMapping[Result[_]]: ResultMapping[Result, Result] = new ResultMapping[Result, Result] {
    def apply[T](result: Result[T]): Result[T] = result
  }
}
