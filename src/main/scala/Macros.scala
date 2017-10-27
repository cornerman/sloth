package sloth.macros

import scala.reflect.macros.blackbox.Context

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
  def impl[Trait, PickleType, Result[_]]
    (c: Context)
    (implicit traitTag: c.WeakTypeTag[Trait], pickleTypeTag: c.WeakTypeTag[PickleType], resultTag: c.WeakTypeTag[Result[_]]): c.Expr[Trait] = {
    import c.universe._

    val t = Translator(c)

    val validMethods = t.supportedMethodsInType(traitTag.tpe, resultTag.tpe)

    val bridgeVal = q"${c.prefix.tree}"

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
          $bridgeVal.execute[$paramListType, $innerReturnType]($path, $paramList)
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
  import sloth.core.{SlothFailure, Request}

  def impl[Trait, PickleType, Result[_]]
    (c: Context)
    (impl: c.Expr[Trait])
    (implicit traitTag: c.WeakTypeTag[Trait], pickleTypeTag: c.WeakTypeTag[PickleType], resultTag: c.WeakTypeTag[Result[_]]): c.Expr[Request[PickleType] => Either[SlothFailure, Result[PickleType]]] = {
    import c.universe._

    val t = Translator(c)

    val validMethods = t.supportedMethodsInType(traitTag.tpe, resultTag.tpe)

    val corePkg = q"_root_.sloth.core"
    val bridgeVal = q"${c.prefix.tree}"

    val methodCases = validMethods.map { case (symbol, method) =>
      val path = t.methodPath(traitTag.tpe, symbol)

      val argsVar = q"args"
      val paramTypes = t.paramTypesInType(method)
      val paramListType = t.hlistType(paramTypes)
      val argParams = List.tabulate(paramTypes.size)(i => q"$argsVar($i)")
      val innerReturnType = method.resultType.typeArgs.head

      cq"""
        $corePkg.Request($path, payload) =>
          $bridgeVal.execute[$paramListType, $innerReturnType](payload) { args =>
            $impl.${symbol.name.toTermName}(..$argParams)
          }
      """
    }

    val defaultCase = cq"""
      $corePkg.Request(path, _) => Left($corePkg.SlothFailure.PathNotFound(path))
    """

    val tree = q"""
      import shapeless._

      {
        case ..$methodCases
        case $defaultCase
      } : $bridgeVal.Router
    """

    println("XXXX: " + tree)
    c.Expr(tree)
  }
}
