package sloth.internal

import scala.reflect.macros.blackbox.Context

class Translator[C <: Context](val c: C) {
  import c.universe._

  val corePkg = q"_root_.sloth.core"
  val serverPkg = q"_root_.sloth.server"
  val clientPkg = q"_root_.sloth.client"
  val internalPkg = q"_root_.sloth.internal"
  val macroThis = q"${c.prefix.tree}"

  private def abort(msg: String) = c.abort(c.enclosingPosition, msg)

  private def valid(check: => Boolean, errorMsg: => String): Either[String, Unit] = Either.cond(check, (), errorMsg)
  private def validateMethod(expectedReturnType: Type, symbol: MethodSymbol, methodType: Type): Either[String, (MethodSymbol, MethodType)] = for {
    _ <- valid(symbol.typeParams.isEmpty, s"method ${symbol.name} has type parameters")
    method = methodType.asInstanceOf[MethodType]
    //TODO: we should support multiple param lists, either with a nested hlist or split or arg count?
    _ <- valid(method.paramLists.size < 2, s"method ${symbol.name} has more than one parameter list: ${method.paramLists}")
    methodResult = method.finalResultType.typeConstructor
    returnResult = expectedReturnType.finalResultType.typeConstructor
    _ <- valid(methodResult <:< returnResult, s"method ${symbol.name} has invalid return type, required: $methodResult <: $returnResult")
  } yield (symbol, method)

  //TODO rename overloaded methods to fun1, fun2, fun3 or append TypeSignature instead of number?
  private def validateAllMethods(methods: List[(MethodSymbol, MethodType)]): List[Either[String, (MethodSymbol, MethodType)]] =
    methods.groupBy(_._1.name).map {
      case (_, x :: Nil) => Right(x)
      case (k, _) => Left(s"method $k is overloaded")
    }.toList

  private def abtractMethodsInType(tpe: Type): List[(MethodSymbol, Type)] = for {
    member <- tpe.members.toList
    if member.isMethod
    symbol = member.asMethod
    if symbol.isAbstract
  } yield (symbol, symbol.typeSignatureIn(tpe))

  def supportedMethodsInType(tpe: Type, expectedReturnType: Type): List[(MethodSymbol, MethodType)] = {
    val methods = abtractMethodsInType(tpe)
    val validatedMethods = methods.map { case (sym, tpe) => validateMethod(expectedReturnType, sym, tpe) }
    val validatedType = eitherSeq(validatedMethods)
      .flatMap(methods => eitherSeq(validateAllMethods(methods)))

    validatedType match {
      case Right(methods) => methods
      case Left(errors) => abort(s"type '$tpe' contains unsupported methods: ${errors.mkString(", ")}")
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
    (implicit traitTag: c.WeakTypeTag[Trait], resultTag: c.WeakTypeTag[Result[_]]): c.Expr[Trait] = Translator(c) { t =>
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

    //TODO why does `new Trait { ..$methods }` not work if methods is empty? cannot instaniate, trait is abstract.
    q"""
      import shapeless._

      val impl = new ${t.internalPkg}.ClientImpl(${t.macroThis})

      class anon extends ${traitTag.tpe.finalResultType} {
        ..$methodImpls
      }

      new anon()
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

    q"""
      import shapeless._, syntax.std.function._

      val impl = new ${t.internalPkg}.ServerImpl(${t.macroThis})

      {
        case ..$methodCases
      } : ${t.serverPkg}.Server.Router[${pickleTypeTag.tpe}, ${resultTag.tpe}]
    """
  }
}
