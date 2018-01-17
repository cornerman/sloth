package sloth

import sloth.core._

import io.circe._, io.circe.parser._, io.circe.syntax._

package object circe {
  implicit def circeWriter[T : Encoder]: Writer[T, String] = new Writer[T, String] {
    override def write(arg: T): String = arg.asJson.noSpaces
  }
  implicit def circeReader[T : Decoder]: Reader[T, String] = new Reader[T, String] {
    override def read(arg: String): Either[Throwable, T] = decode[T](arg)
  }
}
