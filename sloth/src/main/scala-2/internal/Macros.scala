package sloth.internal

import sloth.Method

import scala.reflect.macros.blackbox.Context

object Validator {
  //TODO cats: kleisli? validate?
  type Validation = Either[String, Unit]

  def Valid: Validation = Right(())
  def Invalid(msg: String): Validation = Left(msg)

  def validate(check: => Boolean, errorMsg: => String): Validation = Either.cond(check, (), errorMsg)
}

class Translator[C <: Context](val c: C) {
  import c.universe._
  import Validator._

  object implicits {
    implicit val liftMethod: Liftable[Method] =
      Liftable[Method]{ r => q"new _root_.sloth.Method(${r.apiName}, ${r.methodName})" }
  }

  def abort(msg: String) = c.abort(c.enclosingPosition, msg)

  private def validateMethod(expectedReturnType: Type, symbol: MethodSymbol, methodType: Type): Either[String, (MethodSymbol, Type)] = for {
    _ <- methodType match {
      case _: MethodType | _: NullaryMethodType => Valid
      case _: PolyType => Invalid(s"method ${symbol.name} has type parameters")
      case _ => Invalid(s"method ${symbol.name} has unsupported type")
    }
    methodResult = methodType.finalResultType.typeConstructor
    returnResult = expectedReturnType.finalResultType.typeConstructor
    _ <- validate(methodResult <:< returnResult, s"method ${symbol.name} has invalid return type, required: $methodResult <: $returnResult")
  } yield (symbol, methodType)

  //TODO rename overloaded methods to fun1, fun2, fun3 or append TypeSignature instead of number?
  private def validateAllMethods(methods: List[(MethodSymbol, Type)]): List[Either[String, (MethodSymbol, Type)]] =
    methods.groupBy(m => methodPathPart(m._1)).map {
      case (_, x :: Nil) => Right(x)
      case (k, _) => Left(s"""Method $k is overloaded, please rename one of the methods or use the @Name("other-name") annotation to disambiguate""")
    }.toList

  private def findCustomName(annotations: Seq[Annotation]) = annotations.reverse.map(_.tree).collectFirst {
    case Apply(Select(New(annotation), _), Literal(Constant(name)) :: Nil) if annotation.tpe =:= typeOf[sloth.Name] => name.toString
  }

  private def eitherSeq[A, B](list: List[Either[A, B]]): Either[List[A], List[B]] = list.partition(_.isLeft) match {
    case (Nil, rights) => Right(for (Right(i) <- rights) yield i)
    case (lefts, _)    => Left(for (Left(s) <- lefts) yield s)
  }

  def definedMethodsInType(tpe: Type): List[(MethodSymbol, Type)] = for {
    member <- tpe.members.toList
    if member.isAbstract
    if member.isMethod
    if member.isPublic
    if !member.isConstructor
    if !member.isSynthetic
    symbol = member.asMethod
  } yield (symbol, symbol.typeSignatureIn(tpe))

  def supportedMethodsInType(tpe: Type, expectedReturnType: Type): List[(MethodSymbol, Type)] = {
    val methods = definedMethodsInType(tpe)
    val validatedMethods = methods.map { case (sym, tpe) => validateMethod(expectedReturnType, sym, tpe) }
    val validatedType = eitherSeq(validatedMethods)
      .flatMap(methods => eitherSeq(validateAllMethods(methods)))

    validatedType match {
      case Right(methods) => methods
      case Left(errors) => abort(s"type '$tpe' contains unsupported methods: ${errors.mkString(", ")}")
    }
  }

  //TODO what about fqn for trait to not have overlaps?
  def traitPathPart(tpe: Type): String =
    findCustomName(tpe.typeSymbol.annotations).getOrElse(tpe.typeSymbol.name.toString)

  def methodPathPart(m: MethodSymbol): String =
    findCustomName(m.annotations).getOrElse(m.name.toString)

  def paramAsValDef(p: Symbol): ValDef = q"val ${p.name.toTermName}: ${p.typeSignature}"
  def paramsAsValDefs(m: Type): List[List[ValDef]] = m.paramLists.map(_.map(paramAsValDef))

  def paramsType(tpe: Type): Tree = tpe.paramLists.flatten match {
    case Nil => tq"_root_.scala.Unit"
    case list => tq"(..${list.map(_.typeSignature)})"
  }
  def objectToParams(tpe: Type, obj: TermName): List[List[Tree]] = tpe.paramLists match {
    case Nil => Nil
    case List(Nil) => List(Nil)
    case List(List(_)) => List(List(q"$obj"))
    case lists =>
      var counter = 0
      lists.map(_.map { _ =>
        counter += 1
        q"$obj.${TermName("_" + counter)}"
      })
  }

  def wrapAsParamsType(tpe: Type): Tree = tpe.paramLists.flatten match {
    case Nil => q"()"
    case list => q"""(..${list.map(p => q"${p.name.toTermName}")})"""
  }

  def findIndexOfPlaceholderType(tpe: Type): Int = tpe.typeParams match {
    case typeParam :: _ => tpe.resultType.typeArgs.indexWhere(argTpe => typeParam == argTpe.typeSymbol)
    case _ => -1
  }

  def getInnerTypeOutOfReturnType(tpe: Type, returnType: Type): Type = {
    val placeholderIndex = findIndexOfPlaceholderType(tpe)
    if (placeholderIndex >= 0 && tpe.resultType.typeConstructor =:= returnType.typeConstructor) returnType.typeArgs(placeholderIndex)
    else returnType.typeArgs.last
  }
}

object Translator {
  def apply[T](c: Context)(f: Translator[c.type] => c.Tree): c.Expr[T] = {
    val tree = f(new Translator(c))
   // println(tree)
    c.Expr(tree)
  }
}

object TraitMacro {
  def implBase[Trait, PickleType, Result[_]]
    (c: Context)
    (impl: c.Tree)
    (implicit traitTag: c.WeakTypeTag[Trait], resultTag: c.WeakTypeTag[Result[_]]): c.Expr[Trait] = Translator(c) { t =>
    import t.implicits._
    import c.universe._

    val validMethods = t.supportedMethodsInType(traitTag.tpe, resultTag.tpe)

    val traitPathPart = t.traitPathPart(traitTag.tpe)
    val methodImplList = validMethods.collect { case (symbol, method) =>
      val methodPathPart = t.methodPathPart(symbol)
      val path = Method(traitPathPart, methodPathPart)
      val parameters =  t.paramsAsValDefs(method)
      val paramsType = t.paramsType(method)
      val paramListValue = t.wrapAsParamsType(method)
      val innerReturnType = t.getInnerTypeOutOfReturnType(resultTag.tpe, method.finalResultType)

      q"""
        override def ${symbol.name}(...$parameters): ${method.finalResultType} = {
          impl.execute[${paramsType}, $innerReturnType]($path, $paramListValue)
        }
      """
    }

    val methodImpls = if (methodImplList.isEmpty) List(EmptyTree) else methodImplList

    q"""
      val impl = $impl

      new ${traitTag.tpe.finalResultType} {
        ..$methodImpls
      }
    """
  }

  def impl[Trait, PickleType, Result[_]]
    (c: Context)
    (implicit traitTag: c.WeakTypeTag[Trait], resultTag: c.WeakTypeTag[Result[_]]): c.Expr[Trait] = {
      import c.universe._
      val implTerm = q"new _root_.sloth.internal.ClientImpl(${c.prefix})"
      implBase[Trait, PickleType, Result](c)(implTerm)
    }

  def implContra[Trait, PickleType, Result[_]]
    (c: Context)
    (implicit traitTag: c.WeakTypeTag[Trait], resultTag: c.WeakTypeTag[Result[_]]): c.Expr[Trait] = {
      import c.universe._
      val implTerm = q"new _root_.sloth.internal.ClientContraImpl(${c.prefix})"
      implBase[Trait, PickleType, Result](c)(implTerm)
    }
}

object RouterMacro {
  def implBase[Trait, PickleType, Result[_], Router]
    (c: Context)
    (value: c.Expr[Trait])
    (impl: c.Tree)
    (implicit traitTag: c.WeakTypeTag[Trait], pickleTypeTag: c.WeakTypeTag[PickleType], resultTag: c.WeakTypeTag[Result[_]]): c.Expr[Router] = Translator(c) { t =>
    import c.universe._

    val validMethods = t.supportedMethodsInType(traitTag.tpe, resultTag.tpe)

    val traitPathPart = t.traitPathPart(traitTag.tpe)
    val methodCases = validMethods.map { case (symbol, method) =>
      val methodPathPart = t.methodPathPart(symbol)
      val paramsType = t.paramsType(method)
      val argParams = t.objectToParams(method, TermName("args"))
      val innerReturnType = t.getInnerTypeOutOfReturnType(resultTag.tpe, method.finalResultType)
      val payloadFunction =
        q"""(payload: ${pickleTypeTag.tpe}) => impl.execute[${paramsType}, $innerReturnType](method, payload) { args =>
          value.${symbol.name.toTermName}(...$argParams)
        }"""

      cq"$methodPathPart => Some($payloadFunction)"
    }

    q"""
      val value = $value
      val implRouter = ${c.prefix}
      val impl = $impl

      implRouter.orElse { method =>
        if (method.apiName == $traitPathPart) {
          method.methodName match {
            case ..$methodCases
            case _ => None
          }
        } else None
      }
    """
  }

  def impl[Trait, PickleType, Result[_]]
    (c: Context)
    (value: c.Expr[Trait])
    (implicit traitTag: c.WeakTypeTag[Trait], pickleTypeTag: c.WeakTypeTag[PickleType], resultTag: c.WeakTypeTag[Result[_]]): c.Expr[sloth.RouterCo[PickleType, Result]] = {
      import c.universe._

      val implTerm = q"new _root_.sloth.internal.RouterImpl[${pickleTypeTag.tpe}, ${resultTag.tpe.typeConstructor}](implRouter)"
      implBase[Trait, PickleType, Result, sloth.RouterCo[PickleType, Result]](c)(value)(implTerm)
    }

  def implContra[Trait, PickleType, Result[_]]
    (c: Context)
    (value: c.Expr[Trait])
    (implicit traitTag: c.WeakTypeTag[Trait], pickleTypeTag: c.WeakTypeTag[PickleType], resultTag: c.WeakTypeTag[Result[_]]): c.Expr[sloth.RouterContra[PickleType, Result]] = {
      import c.universe._

      val implTerm = q"new _root_.sloth.internal.RouterContraImpl[${pickleTypeTag.tpe}, ${resultTag.tpe.typeConstructor}](implRouter)"
      implBase[Trait, PickleType, Result, sloth.RouterContra[PickleType, Result]](c)(value)(implTerm)
    }
}
object ChecksumMacro {
  def impl[Trait]
    (c: Context)
    (implicit traitTag: c.WeakTypeTag[Trait]): c.Expr[Int] = Translator(c) { t =>
    import c.universe._

    case class ParamSignature(name: String, tpe: Type) {
      def checksum: Int = (name, typeChecksum(tpe)).hashCode
    }
    case class MethodSignature(name: String, params: List[ParamSignature], result: Type) {
      def checksum: Int = (name, params.map(_.checksum), typeChecksum(result)).hashCode
    }
    case class ApiSignature(name: String, methods: Set[MethodSignature]) {
      def checksum: Int = (name, methods.map(_.checksum)).hashCode
    }

    def paramsOfType(tpe: Type): List[ParamSignature] = tpe match {
      case method: MethodType => method.paramLists.flatMap(_.map { p => ParamSignature(p.name.toString, p.typeSignatureIn(traitTag.tpe)) })
      case _ => Nil
    }

    def unifyClassWithTrait(ttrait: Type, classSym: ClassSymbol): Type = {
      val tclass = classSym.toType
      val traitSeenFromClass = tclass.baseType(ttrait.typeSymbol)
      tclass.substituteTypes(traitSeenFromClass.typeArgs.map(_.typeSymbol), ttrait.typeArgs)
    }

    def ensureClassSymbol(sym: Symbol): ClassSymbol = sym match {
      case sym if sym.isClass => sym.asClass
      case sym => t.abort(s"Type '$sym' is not a class or trait and cannot be checksummed (please file a bug with an example if you think this is wrong)")
    }

    //TODO: expose as separete library
    def typeChecksum(tpe: Type): Int = {
      val classSymbol = ensureClassSymbol(tpe.typeSymbol)

      val directSubClasses = classSymbol.knownDirectSubclasses.map(sym => unifyClassWithTrait(tpe, ensureClassSymbol(sym)))

      val caseAccessors = tpe.members.collect {
        case m if m.isMethod && m.asMethod.isCaseAccessor => m.asMethod
      }

      (
        tpe.typeSymbol.fullName,
        caseAccessors.map(a => (a.name.toString, typeChecksum(a.typeSignatureIn(tpe).finalResultType))),
        directSubClasses.map(typeChecksum).toSet
        ).hashCode
    }

    val definedMethods = t.definedMethodsInType(traitTag.tpe)

    val dataMethods:Set[MethodSignature] = definedMethods.map { case (symbol, method) =>
      val name = t.methodPathPart(symbol)
      val resultType = method.finalResultType
      val params = paramsOfType(method)

      MethodSignature(name, params, resultType)
    }.toSet

    val name = t.traitPathPart(traitTag.tpe)
    val apiSignature = ApiSignature(name, dataMethods)

    val checksum = apiSignature.checksum

    q"""
      $checksum
     """
  }

}
