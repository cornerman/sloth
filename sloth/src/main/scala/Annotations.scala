package sloth

import scala.annotation.StaticAnnotation

final class PathName(val name: String) extends StaticAnnotation

final class MetaName extends StaticAnnotation

trait Meta extends StaticAnnotation
object Meta {
  object http {
    final class Get extends Meta
    final class Head extends Meta
    final class Post extends Meta
    final class Put extends Meta
    final class Delete extends Meta
    final class Options extends Meta
    final class Patch extends Meta
  }
}
