package sloth.macros

import scala.reflect.macros.blackbox.Context

class Translator[C <: Context](val c: C) {
  import c.universe._

  val corePkg = q"_root_.sloth.core"
  val serverPkg = q"_root_.sloth.server"
  val clientPkg = q"_root_.sloth.client"
  val bridgeVal = q"${c.prefix.tree}"

  //TODO: maybe warn about missing functions. will get trait is abstract error in the end.
  //TODO: warn on name clashes or rename in path to name1, name2, name3?
  def supportedMethodsInType(tpe: Type, returnType: Type): Iterable[(MethodSymbol, MethodType)] = for {
    member <- tpe.members
    if member.isMethod
    symbol = member.asMethod
    if symbol.isAbstract
    if symbol.typeParams.isEmpty
    method = symbol.typeSignatureIn(tpe).asInstanceOf[MethodType]
    if method.resultType.typeConstructor <:< returnType.resultType.typeConstructor
  } yield (symbol, method)

  def methodPath(tpe: Type, m: MethodSymbol): List[String] =
    tpe.typeSymbol.name.toString ::
    m.name.toString ::
    Nil

  def paramsAsValDefs(m: MethodType): List[List[ValDef]] =
    m.paramLists.map(_.map(p => q"val ${p.name.toTermName}: ${p.typeSignature}"))

  //TODO multiple param lists?
  def paramValuesAsHList(m: MethodType): Tree =
    m.paramLists.flatten.reverse.foldLeft[Tree](q"HNil")((a, b) => q"${b.name.toTermName} :: $a")

  def paramTypesAsHList(m: MethodType): Tree =
    m.paramLists.flatten.reverse.foldLeft[Tree](tq"HNil")((a, b) => tq"${b.typeSignature} :: $a")
}

object Translator {
  def apply[T](c: Context)(f: Translator[c.type] => c.Tree): c.Expr[T] = {
    val tree = f(new Translator(c))
    println("XXXX: " + tree)
    c.Expr(tree)
  }
}

object TraitMacro {
  def impl[Trait, PickleType, Result[_]]
    (c: Context)
    (implicit traitTag: c.WeakTypeTag[Trait], pickleTypeTag: c.WeakTypeTag[PickleType], resultTag: c.WeakTypeTag[Result[_]]): c.Expr[Trait] = Translator(c) { t =>
    import c.universe._

    val validMethods = t.supportedMethodsInType(traitTag.tpe, resultTag.tpe)

    val methodImpls = validMethods.collect { case (symbol, method) =>
      val path = t.methodPath(traitTag.tpe, symbol)
      val parameters =  t.paramsAsValDefs(method)
      val paramListType = t.paramTypesAsHList(method)
      val paramListValue = t.paramValuesAsHList(method)
      val innerReturnType = method.resultType.typeArgs.head

      q"""
        override def ${symbol.name}(...$parameters): ${method.resultType} = {
          ${t.bridgeVal}.execute[$paramListType, $innerReturnType]($path, $paramListValue)
        }
      """
    }

    q"""
      import shapeless._

      new ${traitTag.tpe.resultType} {
        ..$methodImpls
      }
    """
  }
}

object RouterMacro {
  import sloth.server.Server

  def impl[Trait, PickleType, Result[_]]
    (c: Context)
    (impl: c.Expr[Trait])
    (implicit traitTag: c.WeakTypeTag[Trait], pickleTypeTag: c.WeakTypeTag[PickleType], resultTag: c.WeakTypeTag[Result[_]]): c.Expr[Server.Router[PickleType, Result]] = Translator(c) { t =>
    import c.universe._

    val validMethods = t.supportedMethodsInType(traitTag.tpe, resultTag.tpe)

    val methodCases = validMethods.map { case (symbol, method) =>
      val path = t.methodPath(traitTag.tpe, symbol)
      val paramListType = t.paramTypesAsHList(method)
      val innerReturnType = method.resultType.typeArgs.head

      cq"""
        ${t.corePkg}.Request($path, payload) =>
          ${t.bridgeVal}.execute[$paramListType, $innerReturnType](payload) { args =>
            ($impl.${symbol.name.toTermName} _).toProduct(args)
          }
      """
    }

    val defaultCase = cq"""
      ${t.corePkg}.Request(path, _) => Left(${t.corePkg}.SlothFailure.PathNotFound(path))
    """

    q"""
      import shapeless._, syntax.std.function._

      {
        case ..$methodCases
        case $defaultCase
      } : ${t.serverPkg}.Server.Router[${pickleTypeTag.tpe}, ${resultTag.tpe}]
    """
  }
}
