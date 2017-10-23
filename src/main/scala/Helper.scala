package apitrait.helper

//TODO: better split type params of serializer to support Encoder and Decoder not only Pickler
class DualPicklerFactory[Encoder[_], Decoder[_]] private {
  class DualPickler[T](implicit val encoder: Encoder[T], val decoder: Decoder[T])
  implicit def EncoderWithDecoder[T : Encoder : Decoder]: DualPickler[T] = new DualPickler[T]
}
object DualPicklerFactory{
  def apply[Encode[_], Decode[_]] = new DualPicklerFactory[Encode, Decode]
}
