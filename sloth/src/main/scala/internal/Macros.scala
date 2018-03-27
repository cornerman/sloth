package sloth.internal

import scala.reflect.macros.blackbox.Context
import cats.syntax.either._
import sloth.RequestPath

class Translator[C <: Context](val c: C) {
  import c.universe._

  val slothPkg = q"_root_.sloth"
  val internalPkg = q"_root_.sloth.internal"

  private def abort(msg: String) = c.abort(c.enclosingPosition, msg)

  private def valid(check: => Boolean, errorMsg: => String): Either[String, Unit] = Either.cond(check, (), errorMsg)
  private def validateMethod(expectedReturnType: Type, symbol: MethodSymbol, methodType: Type): Either[String, (MethodSymbol, Type)] = for {
    _ <- methodType match {
      case _: MethodType | _: NullaryMethodType => Right(())
      case _: PolyType => Left(s"method ${symbol.name} has type parameters")
      case _ => Left(s"method ${symbol.name} has unsupported type")
    }
    methodResult = methodType.finalResultType.typeConstructor
    returnResult = expectedReturnType.finalResultType.typeConstructor
    _ <- valid(methodResult <:< returnResult, s"method ${symbol.name} has invalid return type, required: $methodResult <: $returnResult")
  } yield (symbol, methodType)

  //TODO rename overloaded methods to fun1, fun2, fun3 or append TypeSignature instead of number?
  private def validateAllMethods(methods: List[(MethodSymbol, Type)]): List[Either[String, (MethodSymbol, Type)]] =
    methods.groupBy(m => methodPathPart(m._1)).map {
      case (_, x :: Nil) => Right(x)
      case (k, ms) => Left(s"""method $k is overloaded (rename the method or add a @PathName("other-name"))""")
    }.toList

  private def findPathName(annotations: Seq[Annotation]) = annotations.reverse.map(_.tree).collectFirst {
    case Apply(Select(New(annotation), _), Literal(Constant(name)) :: Nil) if annotation.tpe =:= typeOf[sloth.PathName] => name.toString
  }

  private def definedMethodsInType(tpe: Type): List[(MethodSymbol, Type)] = for {
    member <- tpe.decls.toList
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

  def pathTree(path: RequestPath) = q"$slothPkg.RequestPath(${path.apiName}, ${path.methodName})"

  //TODO what about fqn for trait to not have overlaps?
  def traitPath(tpe: Type): String =
    findPathName(tpe.typeSymbol.annotations).getOrElse(tpe.typeSymbol.name.toString)

  def methodPathPart(m: MethodSymbol): String =
    findPathName(m.annotations).getOrElse(m.name.toString)

  def paramAsValDef(p: Symbol): ValDef = q"val ${p.name.toTermName}: ${p.typeSignature}"
  def paramsAsValDefs(m: Type): List[List[ValDef]] = m.paramLists.map(_.map(paramAsValDef))

  def paramsObjectName(path: RequestPath) = s"_sloth_${path.apiName}_${path.methodName}"
  case class ParamsObject(tree: Tree, tpe: Tree)
  def paramsAsObject(tpe: Type, path: RequestPath): ParamsObject = {
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

  def newParamsObject(tpe: Type, path: RequestPath): Tree = {
    val params = tpe.paramLists.flatten
    val name = paramsObjectName(path)
    val typeName = TypeName(name)

    params match {
      case Nil => q"$slothPkg.Arguments.Empty"
      case list => q"""new $typeName(..${params.map(p => q"${p.name.toTermName}")})"""
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
  def impl[Trait, PickleType, Result[_], ErrorType]
    (c: Context)
    (implicit traitTag: c.WeakTypeTag[Trait], resultTag: c.WeakTypeTag[Result[_]]): c.Expr[Trait] = Translator(c) { t =>
    import c.universe._

    val validMethods = t.supportedMethodsInType(traitTag.tpe, resultTag.tpe)

    val traitPath = t.traitPath(traitTag.tpe)
    val (methodImplList, paramsObjects) = validMethods.collect { case (symbol, method) if symbol.isAbstract =>
      val methodPathPart = t.methodPathPart(symbol)
      val path = RequestPath(traitPath, methodPathPart)
      val parameters =  t.paramsAsValDefs(method)
      val paramsObject = t.paramsAsObject(method, path)
      val paramListValue = t.newParamsObject(method, path)
      val innerReturnType = method.finalResultType.typeArgs.head

      (q"""
        override def ${symbol.name}(...$parameters): ${method.finalResultType} = {
          impl.execute[${paramsObject.tpe}, $innerReturnType](${t.pathTree(path)}, $paramListValue)
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

    val validMethods = t.supportedMethodsInType(traitTag.tpe, resultTag.tpe)

    val traitPath = t.traitPath(traitTag.tpe)
    val (methodTuples, paramsObjects) = validMethods.map { case (symbol, method) =>
      val methodPathPart = t.methodPathPart(symbol)
      val path = RequestPath(traitPath, methodPathPart)
      val paramsObject = t.paramsAsObject(method, path)
      val argParams: List[List[Tree]] = t.objectToParams(method, TermName("args"))
      val innerReturnType = method.finalResultType.typeArgs.head
      val payloadFunction =
        q"""(payload: ${pickleTypeTag.tpe}) => impl.execute[${paramsObject.tpe}, $innerReturnType](${t.pathTree(path)}, payload) { args =>
          value.${symbol.name.toTermName}(...$argParams)
        }"""

      (q"($methodPathPart, $payloadFunction)", paramsObject.tree)
    }.unzip

    q"""
      val value = $value
      val impl = new ${t.internalPkg}.RouterImpl[${pickleTypeTag.tpe}, ${resultTag.tpe.typeConstructor}]()($functor)

      ..$paramsObjects

      ${c.prefix}.orElse($traitPath, scala.collection.mutable.HashMap(..$methodTuples))

    """
  }
}
