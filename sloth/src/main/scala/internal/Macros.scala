package sloth.internal

import scala.reflect.macros.blackbox.Context
import cats.syntax.either._
import collection.breakOut

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

  val slothPkg = q"_root_.sloth"
  val internalPkg = q"_root_.sloth.internal"

  def abort(msg: String) = c.abort(c.enclosingPosition, msg)

  private def validateMethod(symbol: MethodSymbol): Either[String, MethodSymbol] = for {
    _ <- symbol.typeSignature match {
      case _: MethodType | _: NullaryMethodType => Valid
      case _: PolyType => Invalid(s"method ${symbol.name} has type parameters")
      case _ => Invalid(s"method ${symbol.name} has unsupported type")
    }
  } yield symbol

  //TODO rename overloaded methods to fun1, fun2, fun3 or append TypeSignature instead of number?
  private def validateAllMethods(methods: List[MethodSymbol]): List[Either[String, MethodSymbol]] =
    methods.groupBy(methodPathPart(_)).map {
      case (_, x :: Nil) => Right(x)
      case (k, ms) => Left(s"""method $k is overloaded (rename the method or add a @PathName("other-name"))""")
    }.toList

  private def findPathName(annotations: Seq[Annotation]) = annotations.reverse.map(_.tree).collectFirst {
    case Apply(Select(New(annotation), _), Literal(Constant(name)) :: Nil) if annotation.tpe =:= typeOf[sloth.PathName] => name.toString
  }

  def definedMethodsInType(tpe: Type): List[MethodSymbol] = for {
    member <- tpe.decls.toList
    if member.isMethod
    if member.isPublic
    if member.isAbstract
    if !member.isConstructor
    if !member.isSynthetic
    symbol = member.asMethod
  } yield symbol

  def supportedMethodsInType(tpe: Type): List[MethodSymbol] = {
    val methods = definedMethodsInType(tpe)
    val validatedMethods = methods.map { case sym => validateMethod(sym) }
    val validatedType = eitherSeq(validatedMethods)
      .flatMap(methods => eitherSeq(validateAllMethods(methods)))

    validatedType match {
      case Right(methods) => methods
      case Left(errors) => abort(s"type '$tpe' contains unsupported methods: ${errors.mkString(", ")}")
    }
  }

  //TODO what about fqn for trait to not have overlaps?
  def traitPathPart(tpe: Type): String =
    findPathName(tpe.typeSymbol.annotations).getOrElse(tpe.typeSymbol.name.toString)

  def methodPathPart(m: MethodSymbol): String =
    findPathName(m.annotations).getOrElse(m.name.toString)

  def paramAsValDef(p: Symbol): ValDef = q"val ${p.name.toTermName}: ${p.typeSignature}"
  def paramsAsValDefs(m: Type): List[List[ValDef]] = m.paramLists.map(_.map(paramAsValDef))

  def paramsObjectName(path: List[String]) = s"_sloth_${path.mkString("_")}"
  case class ParamsObject(tree: Tree, tpe: Tree)
  def paramsAsObject(tpe: Type, path: List[String]): ParamsObject = {
    val params = tpe.paramLists.flatten
    val name = paramsObjectName(path)
    val typeName = TypeName(name)

    params match {
      case Nil => ParamsObject(
        tree = EmptyTree,
        tpe = tq"$slothPkg.Arguments.Empty.type"
      )
      //TODO extends AnyVal (but value class may not be a local class)
      // case head :: Nil => ParamsObject(
      //   tree = q"case class $typeName(${paramAsValDef(head)}) extends AnyVal",
      //   tpe = tq"$typeName"
      // )
      case list => ParamsObject(
        tree = q"case class $typeName(..${list.map(paramAsValDef)})",
        tpe = tq"$typeName"
      )
    }
  }
  def objectToParams(tpe: Type, obj: TermName): List[List[Tree]] =
    tpe.paramLists.map(_.map(p => q"$obj.${p.name.toTermName}"))

  def newParamsObject(tpe: Type, path: List[String]): Tree = {
    val params = tpe.paramLists.flatten
    val name = paramsObjectName(path)
    val typeName = TypeName(name)

    params match {
      case Nil => q"$slothPkg.Arguments.Empty"
      case list => q"""new $typeName(..${params.map(p => q"${p.name.toTermName}")})"""
    }
  }

  def findOuterAndInnerReturnType(tpe: Type, resolvedTpe: Type): (Type, Type) = tpe.typeArgs match {
    case Nil => (typeOf[cats.Id[_]].typeConstructor, tpe)
    case t :: Nil => (resolvedTpe.typeConstructor, t)
    case args =>
      val concreteArgs = args.zipWithIndex.filterNot(_._1.takesTypeArgs)
      if (concreteArgs.size == 1) {
        val (concreteArg, index) = concreteArgs.head
        val tSym = resolvedTpe.typeConstructor.typeParams(index)
        val tTpe = internal.typeRef(NoPrefix, tSym, Nil)
        val substituted = appliedType(resolvedTpe.typeConstructor, args.updated(index, tTpe))
        val polyType = c.internal.polyType(tSym :: Nil, substituted)
        (polyType, concreteArg)
      } else {
        //TODO: put into validate method
        abort(s"Return type '$tpe' of method has multiple fitting type arguments, this is not supported in Api traits. You can workaround this by defining a type alias `F[T] = ${tpe.typeConstructor}[..., T, ...]` and using `F` as return type.")
      }
  }

  def inferImplicitResultMapping(from: Type, to: Type): Tree = {
    val rawMapping = typeOf[sloth.ResultMapping[Any, Any]]
    val typeArgs = from :: to :: Nil
    val mapping = internal.typeRef(NoPrefix, rawMapping.typeConstructor.typeSymbol, typeArgs)
    c.inferImplicitValue(mapping) match {
      case EmptyTree => abort(s"""Cannot find implicit mapping '$mapping' for occurring result types. Define it with:
        |  implicit val mapping: $mapping = new $mapping {
          |    def apply[T](result: $from[T]): $to[T] = ???
        |  }
        """.stripMargin)
      case tree => tree
    }
  }
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

    val validMethods = t.supportedMethodsInType(traitTag.tpe)

    val traitPathPart = t.traitPathPart(traitTag.tpe)
    val (methodImplList, paramsObjects) = validMethods.map { symbol =>
      val methodPathPart = t.methodPathPart(symbol)
      val path = traitPathPart :: methodPathPart :: Nil
      val method = symbol.typeSignatureIn(traitTag.tpe)
      val parameters =  t.paramsAsValDefs(method)
      val paramsObject = t.paramsAsObject(method, path)
      val paramListValue = t.newParamsObject(method, path)
      val (outerReturnType, innerReturnType) = t.findOuterAndInnerReturnType(symbol.returnType, method.finalResultType)
      val resultMapping = t.inferImplicitResultMapping(from = resultTag.tpe.typeConstructor, to = outerReturnType)

      (q"""
        override def ${symbol.name}(...$parameters): ${method.finalResultType} = {
          val resultMapping = $resultMapping
          val result = impl.execute[${paramsObject.tpe}, $innerReturnType]($path, $paramListValue)
          resultMapping(result)
        }
      """, paramsObject.tree)
    }.unzip
    val methodImpls = if (methodImplList.isEmpty) List(EmptyTree) else methodImplList

    q"""
      val impl = new ${t.internalPkg}.ClientImpl(${c.prefix})

      ..$paramsObjects
      new ${traitTag.tpe.finalResultType} {
        ..$methodImpls
      }
    """
  }
}

object RouterMacro {
  def impl[Trait, PickleType, Result[_]]
    (c: Context)
    (value: c.Expr[Trait])
    (functor: c.Expr[cats.Functor[Result]])
    (implicit traitTag: c.WeakTypeTag[Trait], pickleTypeTag: c.WeakTypeTag[PickleType], resultTag: c.WeakTypeTag[Result[_]]): c.Expr[sloth.Router[PickleType, Result]] = Translator(c) { t =>
    import c.universe._

    val validMethods = t.supportedMethodsInType(traitTag.tpe)

    val traitPathPart = t.traitPathPart(traitTag.tpe)
    val (methodTuples, paramsObjects) = validMethods.map { symbol =>
      val method = symbol.typeSignatureIn(traitTag.tpe)
      val methodPathPart = t.methodPathPart(symbol)
      val path = traitPathPart :: methodPathPart :: Nil
      val paramsObject = t.paramsAsObject(method, path)
      val argParams: List[List[Tree]] = t.objectToParams(method, TermName("args"))
      val (outerReturnType, innerReturnType) = t.findOuterAndInnerReturnType(symbol.returnType, method.finalResultType)
      val resultMapping = t.inferImplicitResultMapping(from = outerReturnType, to = resultTag.tpe.typeConstructor)

      val payloadFunction =
        q"""(payload: ${pickleTypeTag.tpe}) => {
          val resultMapping = $resultMapping
          impl.execute[${paramsObject.tpe}, $innerReturnType]($path, payload) { args =>
            val result = value.${symbol.name.toTermName}(...$argParams)
            resultMapping(result)
          }
        }
        """

      (q"($methodPathPart, $payloadFunction)", paramsObject.tree)
    }.unzip

    q"""
      val value = $value
      val impl = new ${t.internalPkg}.RouterImpl[${pickleTypeTag.tpe}, ${resultTag.tpe.typeConstructor}]()($functor)

      ..$paramsObjects

      ${c.prefix}.orElse($traitPathPart, scala.collection.mutable.HashMap(..$methodTuples))

    """
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

    val dataMethods:Set[MethodSignature] = definedMethods.map { symbol =>
      val method = symbol.typeSignatureIn(traitTag.tpe)
      val name = t.methodPathPart(symbol)
      val resultType = method.finalResultType
      val params = paramsOfType(method)

      MethodSignature(name, params, resultType)
    }(breakOut)

    val name = t.traitPathPart(traitTag.tpe)
    val apiSignature = ApiSignature(name, dataMethods)

    val checksum = apiSignature.checksum

    q"""
      $checksum
     """
  }

}
