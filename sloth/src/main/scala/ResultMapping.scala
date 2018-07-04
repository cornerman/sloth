package sloth

import cats.~>

trait ResultMapping[From[_], To[_]] extends (From ~> To) { mapping =>
  def apply[T](result: From[T]): To[T]
  final def mapK[R[_]](f: To ~> R): ResultMapping[From, R] = new ResultMapping[From, R] {
    def apply[T](result: From[T]): R[T] = f(mapping(result))
  }
  final def contramapK[R[_]](f: R ~> From): ResultMapping[R, To] = new ResultMapping[R, To] {
    def apply[T](result: R[T]): To[T] = mapping(f(result))
  }
}
object ResultMapping {
  implicit def identityMapping[Result[_]]: ResultMapping[Result, Result] = new ResultMapping[Result, Result] {
    def apply[T](result: Result[T]): Result[T] = result
  }

  def apply[From[_], To[_]](f: From ~> To): ResultMapping[From, To] = new ResultMapping[From, To] {
    def apply[T](result: From[T]): To[T] = f(result)
  }
}
