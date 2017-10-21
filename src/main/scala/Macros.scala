package apitrait.macros

import scala.reflect.macros.blackbox.Context
import scala.language.experimental.macros
import scala.util.{Success, Failure}

class Translator[C <: Context](val c: C) {
  import c.universe._

  val prelude = q"""
    import shapeless._
  """

  def abstractMethods(tpe: Type): Iterable[MethodSymbol] = for {
    member <- tpe.members
    if member.isMethod
    method = member.asMethod
    if method.isAbstract
  } yield method

  def methodReturnsSubType(m: MethodSymbol, tpe: Type): Boolean =
    if (m.returnType.typeArgs.size == tpe.typeArgs.size) {
      val typeArgSymbols = tpe.typeArgs.map(_.typeSymbol)
      val fittedType = tpe.substituteTypes(typeArgSymbols, m.returnType.typeArgs)
      m.returnType <:< fittedType
    } else false

  def methodPath(tpe: Type, m: MethodSymbol): List[String] =
    tpe.typeSymbol.name.toString ::
    m.name.toString ::
    Nil

  //TODO multiple param lists?
  def methodParamTypes(m: MethodSymbol): List[Type] =
    m.paramLists.flatMap(_.map(_.typeSignature)).toList

  def methodParamTerms(m: MethodSymbol): List[TermName] =
    m.paramLists.flatMap(_.map(_.name.toTermName)).toList

  def buildHList[T : Liftable](list: List[T]) =
    list.reverse.foldLeft[Tree](q"HNil")((a, b) => q"$b :: $a")
}

object Translator {
  def apply(c: Context): Translator[c.type] = new Translator(c)
}

//TODO: what about generic functions?

object TraitMacro {
  def impl[Trait, Pickler[_], Result[_], PickleType]
    (c: Context)
    // (impl: c.Expr[Trait])
    (implicit traitTag: c.WeakTypeTag[Trait], picklerTag: c.WeakTypeTag[Pickler[_]], resultTag: c.WeakTypeTag[Result[_]], pickleTypeTag: c.WeakTypeTag[PickleType]): c.Expr[Trait] = {
    import c.universe._

    val t = Translator(c)

    val abstractMethods = t.abstractMethods(traitTag.tpe)
    val validMethods = abstractMethods.filter(t.methodReturnsSubType(_, resultTag.tpe))

    val bridgeVal = q"${c.prefix.tree}.bridge"

    val methodImpls = validMethods.map { method =>
      val path = t.methodPath(traitTag.tpe, method)
      val parameters =  method.paramLists.map(_.map { param =>
        q"val ${param.name.toTermName}: ${param.typeSignature}"
      })

      val paramTypes = t.methodParamTypes(method)
      val paramTerms = t.methodParamTerms(method)
      val paramListType = t.buildHList(paramTypes)
      val paramList = t.buildHList(paramTerms)
      val returnType = method.returnType.typeArgs.head

      q"""
        def ${method.name}(...$parameters): ${method.returnType} = {
          $bridgeVal.call[$paramListType, $returnType]($path, $paramList)
        }
      """
    }

    val tree = q"""
      ${t.prelude}

      new ${traitTag.tpe} {
        ..$methodImpls
      }
    """

    println("XXXX: " + tree)
    c.Expr(tree)
  }
}

object RouterMacro {
  import apitrait.core.Request

  def impl[Trait, Pickler[_], Result[_], PickleType]
    (c: Context)
    (impl: c.Expr[Trait])
    (implicit traitTag: c.WeakTypeTag[Trait], picklerTag: c.WeakTypeTag[Pickler[_]], resultTag: c.WeakTypeTag[Result[_]], pickleTypeTag: c.WeakTypeTag[PickleType]): c.Expr[PartialFunction[Request[PickleType], Result[PickleType]]] = {
    import c.universe._

    val t = Translator(c)

    val abstractMethods = t.abstractMethods(traitTag.tpe)
    val validMethods = abstractMethods.filter(t.methodReturnsSubType(_, resultTag.tpe))

    val bridgeVal = q"${c.prefix.tree}.bridge"
    val corePkg = q"_root_.apitrait.core"

    val methodCases = validMethods.map { method =>
      val path = t.methodPath(traitTag.tpe, method)

      val paramTypes = t.methodParamTypes(method)
      val paramListType = t.buildHList(paramTypes)
      val argParams = List.tabulate(paramTypes.size)(i => q"args($i)")

      cq"""
        $corePkg.Request($path, payload) =>
          val args = $bridgeVal.deserialize[$paramListType](payload)
          $impl.${method.name.toTermName}(..$argParams)
      """
    }

    val tree = q"""
      ${t.prelude}

      {
        case ..$methodCases
      } : PartialFunction[$corePkg.Request[${pickleTypeTag.tpe}], ${resultTag.tpe.typeSymbol}[${pickleTypeTag.tpe}]]
    """

    println("XXXX: " + tree)
    c.Expr(tree)
  }
}
