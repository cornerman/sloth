package sloth

import scala.annotation.StaticAnnotation

class PathName(val name: String) extends StaticAnnotation

sealed trait SerializeParametersAs extends StaticAnnotation
object SerializeParametersAs {
  class CaseClass extends SerializeParametersAs
  class Tuple extends SerializeParametersAs
  class Single extends SerializeParametersAs
}
