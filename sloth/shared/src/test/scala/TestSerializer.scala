package test.sloth

import sloth.core._
import scala.util.Try

object TestSerializer {
  type PickleType = Any

  implicit def anyWriter[T]: Writer[T, PickleType] = new Writer[T, PickleType] {
    override def write(arg: T): PickleType = arg
  }
  implicit def anyReader[T]: Reader[T, PickleType] = new Reader[T, PickleType] {
    override def read(arg: PickleType): Either[Throwable, T] = Try(arg.asInstanceOf[T]).toEither
  }
}
