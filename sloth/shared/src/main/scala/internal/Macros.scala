package sloth.internal

import sloth.helper.EitherHelper

import scala.reflect.macros.blackbox.Context

class Translator[C <: Context](val c: C) {
  import c.universe._

  val corePkg = q"_root_.sloth.core"
  val serverPkg = q"_root_.sloth.server"
  val clientPkg = q"_root_.sloth.client"
  val internalPkg = q"_root_.sloth.internal"
  val bridgeVal = q"${c.prefix.tree}"

  private def abort(msg: String) = c.abort(c.enclosingPosition, msg)

  private def valid(check: => Boolean, errorMsg: => String): Either[String, Unit] = Either.cond(check, (), errorMsg)
  private def validateMethod(tpe: Type, returnType: Type, symbol: MethodSymbol): Either[String, (MethodSymbol, MethodType)] = for {
    _ <- valid(symbol.typeParams.isEmpty, s"method ${symbol.name} has type parameters")
    method = symbol.typeSignatureIn(tpe).asInstanceOf[MethodType]
    //TODO: we should support multiple param lists, either with a nested hlist or split or arg count?
    _ <- valid(method.paramLists.size < 2, s"method ${symbol.name} has more than one parameter list: ${method.paramLists}")
    methodResult = method.finalResultType.typeConstructor
    returnResult = returnType.finalResultType.typeConstructor
    _ <- valid(methodResult <:< returnResult, s"method ${symbol.name} has invalid return type, required: $methodResult <: $returnResult")
  } yield (symbol, method)

  private def validateIndividualMethods(tpe: Type, returnType: Type): List[Either[String, (MethodSymbol, MethodType)]] = for {
    member <- tpe.members.toList
    if member.isMethod
    symbol = member.asMethod
    if symbol.isAbstract
  } yield validateMethod(tpe, returnType, symbol)

  //TODO rename overloaded methods to fun1, fun2, fun3 or append TypeSignature instead of number?
  private def validateAllMethods(methods: List[(MethodSymbol, MethodType)]): List[Either[String, (MethodSymbol, MethodType)]] =
    methods.groupBy(_._1.name).map {
      case (_, x :: Nil) => Right(x)
      case (k, _) => Left(s"method $k is overloaded")
    }.toList

  def supportedMethodsInType(tpe: Type, returnType: Type): List[(MethodSymbol, MethodType)] = {
    val methods = validateIndividualMethods(tpe, returnType)
    val sequence = EitherHelper.sequence(methods).flatMap(methods => EitherHelper.sequence(validateAllMethods(methods)))

    sequence match {
      case Left(errors) => abort(s"type '$tpe' contains unsupported methods: ${errors.mkString(", ")}")
      case Right(methods) => methods
    }
  }

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
    // println("XXX: " + tree)
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
      val innerReturnType = method.finalResultType.typeArgs.head

      q"""
        override def ${symbol.name}(...$parameters): ${method.finalResultType} = {
          impl.execute[$paramListType, $innerReturnType]($path, $paramListValue)
        }
      """
    }

    q"""
      import shapeless._

      val impl = new ${t.internalPkg}.ClientImpl(${t.bridgeVal})

      new ${traitTag.tpe.finalResultType} {
        ..$methodImpls
      }
    """
  }
}

object RouterMacro {
  import sloth.server.Server

  def impl[Trait, PickleType, Result[_]]
    (c: Context)
    (value: c.Expr[Trait])
    (implicit traitTag: c.WeakTypeTag[Trait], pickleTypeTag: c.WeakTypeTag[PickleType], resultTag: c.WeakTypeTag[Result[_]]): c.Expr[Server.Router[PickleType, Result]] = Translator(c) { t =>
    import c.universe._

    val validMethods = t.supportedMethodsInType(traitTag.tpe, resultTag.tpe)

    val methodCases = validMethods.map { case (symbol, method) =>
      val path = t.methodPath(traitTag.tpe, symbol)
      val paramListType = t.paramTypesAsHList(method)
      val innerReturnType = method.finalResultType.typeArgs.head

      cq"""
        ${t.corePkg}.Request($path, payload) =>
          impl.execute[$paramListType, $innerReturnType](payload) { args =>
            ($value.${symbol.name.toTermName} _).toProduct(args)
          }
      """
    }

    val defaultCase = cq"""
      ${t.corePkg}.Request(path, _) => Left(${t.corePkg}.SlothFailure.PathNotFound(path))
    """

    q"""
      import shapeless._, syntax.std.function._

      val impl = new ${t.internalPkg}.ServerImpl(${t.bridgeVal})

      {
        case ..$methodCases
        case $defaultCase
      } : ${t.serverPkg}.Server.Router[${pickleTypeTag.tpe}, ${resultTag.tpe}]
    """
  }
}
