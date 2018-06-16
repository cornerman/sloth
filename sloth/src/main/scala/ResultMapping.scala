package sloth

import cats.~>

trait ResultMapping[From[_], To[_]] extends (From ~> To) {
  def apply[T](result: From[T]): To[T]
}
object ResultMapping {
  implicit def identityMapping[Result[_]]: ResultMapping[Result, Result] = new ResultMapping[Result, Result] {
    def apply[T](result: Result[T]): Result[T] = result
  }

  def apply[From[_], To[_]](f: From ~> To): ResultMapping[From, To] = new ResultMapping[From, To] {
    def apply[T](result: From[T]): To[T] = f(result)
  }
}
