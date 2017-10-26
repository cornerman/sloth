package sloth.core

case class Request[T](path: List[String], payload: T)

trait Serializer[Encoder[_], Decoder[_], PickleType] {
  def serialize[T : Encoder](arg: T): PickleType
  def deserialize[T : Decoder](arg: PickleType): T
}

trait CanMap[Result[_]] {
  def apply[T, S](t: Result[T])(f: T => S): Result[S]
}

trait RequestTransport[Result[_], PickleType] {
  def apply(request: Request[PickleType]): Result[PickleType]
}
