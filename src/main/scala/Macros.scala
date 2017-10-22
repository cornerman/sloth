package apitrait.macros

import scala.reflect.macros.blackbox.Context
import scala.language.experimental.macros
import scala.util.{Success, Failure}

class Translator[C <: Context](val c: C) {
  import c.universe._

  def methodReturnsSubType(method: MethodType, returnType: Type): Boolean = {
    val methodType = method.resultType
    if (methodType.typeArgs.size == returnType.typeArgs.size) {
      val typeArgSymbols = returnType.typeArgs.map(_.typeSymbol)
      val fittedType = returnType.substituteTypes(typeArgSymbols, methodType.typeArgs)
      println(methodType)
      println(fittedType.resultType)
      methodType <:< fittedType.resultType
    } else false
  }

  def supportedMethodsInType(tpe: Type, returnType: Type): Iterable[(MethodSymbol, MethodType)] = for {
    member <- tpe.members
    if member.isMethod
    symbol = member.asMethod
    if symbol.isAbstract
    if symbol.typeParams.isEmpty
    method = symbol.typeSignatureIn(tpe).asInstanceOf[MethodType]
    if methodReturnsSubType(method, returnType)
  } yield (symbol, method)

  def methodPath(tpe: Type, m: MethodSymbol): List[String] =
    tpe.typeSymbol.name.toString ::
    m.name.toString ::
    Nil

  //TODO multiple param lists?
  def paramTypesInType(m: MethodType): List[Type] =
    m.paramLists.flatMap(_.map(_.typeSignature)).toList

  def paramTermsInType(m: MethodType): List[TermName] =
    m.paramLists.flatMap(_.map(_.name.toTermName)).toList

  def hlistType(list: List[Type]) =
    list.reverse.foldLeft[Tree](tq"HNil")((a, b) => tq"$b :: $a")

  def hlistTerm(list: List[TermName]) =
    list.reverse.foldLeft[Tree](q"HNil")((a, b) => q"$b :: $a")
}

object Translator {
  def apply(c: Context): Translator[c.type] = new Translator(c)
}

object TraitMacro {
  def impl[Trait, Pickler[_], Result[_], PickleType]
    (c: Context)
    (implicit traitTag: c.WeakTypeTag[Trait], picklerTag: c.WeakTypeTag[Pickler[_]], resultTag: c.WeakTypeTag[Result[_]], pickleTypeTag: c.WeakTypeTag[PickleType]): c.Expr[Trait] = {
    import c.universe._

    val t = Translator(c)

    val validMethods = t.supportedMethodsInType(traitTag.tpe, resultTag.tpe)

    val bridgeVal = q"${c.prefix.tree}"
    val corePkg = q"_root_.apitrait.core"

    val methodImpls = validMethods.collect { case (symbol, method) =>
      val path = t.methodPath(traitTag.tpe, symbol)
      val parameters =  method.paramLists.map(_.map { param =>
        q"val ${param.name.toTermName}: ${param.typeSignature}"
      })

      val paramTypes = t.paramTypesInType(method)
      val paramTerms = t.paramTermsInType(method)
      val paramListType = t.hlistType(paramTypes)
      val paramList = t.hlistTerm(paramTerms)
      val innerReturnType = method.resultType.typeArgs.head

      q"""
        def ${symbol.name}(...$parameters): ${method.resultType} = {
          val params = $bridgeVal.serializer.serialize[$paramListType]($paramList)
          val request = $corePkg.Request[${pickleTypeTag.tpe}]($path, params)
          val result = $bridgeVal.transport(request)
          $bridgeVal.canMap(result)(x => $bridgeVal.serializer.deserialize[$innerReturnType](x))
        }
      """
    }

    val tree = q"""
      import shapeless._

      new ${traitTag.tpe.resultType} {
        ..$methodImpls
      }
    """

    println("XXXX: " + tree)
    c.Expr(tree)
  }
}

object RouterMacro {
  import apitrait.core.Request

  def impl[Trait , Pickler[_], Result[_], PickleType]
    (c: Context)
    (impl: c.Expr[Trait])
    (implicit traitTag: c.WeakTypeTag[Trait], picklerTag: c.WeakTypeTag[Pickler[_]], resultTag: c.WeakTypeTag[Result[_]], pickleTypeTag: c.WeakTypeTag[PickleType]): c.Expr[PartialFunction[Request[PickleType], Result[PickleType]]] = {
    import c.universe._

    val t = Translator(c)

    val validMethods = t.supportedMethodsInType(traitTag.tpe, resultTag.tpe)

    val bridgeVal = q"${c.prefix.tree}"
    val corePkg = q"_root_.apitrait.core"

    val methodCases = validMethods.map { case (symbol, method) =>
      val path = t.methodPath(traitTag.tpe, symbol)

      val paramTypes = t.paramTypesInType(method)
      val paramListType = t.hlistType(paramTypes)
      val argParams = List.tabulate(paramTypes.size)(i => q"args($i)")
      val innerReturnType = method.resultType.typeArgs.head

      cq"""
        $corePkg.Request($path, payload) =>
          val args = $bridgeVal.serializer.deserialize[$paramListType](payload)
          val result = $impl.${symbol.name.toTermName}(..$argParams)
          $bridgeVal.canMap(result)(x => $bridgeVal.serializer.serialize[$innerReturnType](x))
      """
    }

    val tree = q"""
      import shapeless._

      {
        case ..$methodCases
      } : PartialFunction[$corePkg.Request[${pickleTypeTag.tpe}], ${resultTag.tpe.typeSymbol}[${pickleTypeTag.tpe}]]
    """

    println("XXXX: " + tree)
    c.Expr(tree)
  }
}
