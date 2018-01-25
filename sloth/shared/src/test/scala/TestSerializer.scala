package test.sloth

import chameleon._

import scala.util.Try

object TestSerializer {
  type PickleType = Any

  implicit def anySerializer[T]: Serializer[T, PickleType] = new Serializer[T, PickleType] {
    override def serialize(arg: T): PickleType = arg
  }
  implicit def anyDeserializer[T]: Deserializer[T, PickleType] = new Deserializer[T, PickleType] {
    override def deserialize(arg: PickleType): Either[Throwable, T] = Try(arg.asInstanceOf[T]).toEither
  }
}
