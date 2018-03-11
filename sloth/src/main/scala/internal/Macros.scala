package sloth.internal

import scala.reflect.macros.blackbox.Context
import cats.syntax.either._

class Translator[C <: Context](val c: C) {
  import c.universe._

  val slothPkg = q"_root_.sloth"
  val internalPkg = q"_root_.sloth.internal"
  val macroThis = q"${c.prefix.tree}"

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
  private def validateAllMethods(tpe: Type, methods: List[(MethodSymbol, Type)]): List[Either[String, (MethodSymbol, Type)]] =
    methods.groupBy(m => methodPath(tpe, m._1)).map {
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
    symbol = member.asMethod
  } yield (symbol, symbol.typeSignatureIn(tpe))

  def supportedMethodsInType(tpe: Type, expectedReturnType: Type): List[(MethodSymbol, Type)] = {
    val methods = definedMethodsInType(tpe)
    val validatedMethods = methods.map { case (sym, tpe) => validateMethod(expectedReturnType, sym, tpe) }
    val validatedType = eitherSeq(validatedMethods)
      .flatMap(methods => eitherSeq(validateAllMethods(tpe, methods)))

    validatedType match {
      case Right(methods) => methods
      case Left(errors) => abort(s"type '$tpe' contains unsupported methods: ${errors.mkString(", ")}")
    }
  }

  def methodPath(tpe: Type, m: MethodSymbol): List[String] =
    findPathName(tpe.typeSymbol.annotations).getOrElse(tpe.typeSymbol.name.toString) ::
    findPathName(m.annotations).getOrElse(m.name.toString) ::
    Nil


  def paramAsValDef(p: Symbol): ValDef = q"val ${p.name.toTermName}: ${p.typeSignature}"
  def paramsAsValDefs(m: Type): List[List[ValDef]] = m.paramLists.map(_.map(paramAsValDef))

  def paramsObjectName(path: List[String]) = "_sloth_" + path.mkString("_")
  case class ParamsObject(tree: Tree, tpe: Tree)
  def paramsAsObject(tpe: Type, path: List[String]): ParamsObject = {
    val params = tpe.paramLists.flatten
    val name = paramsObjectName(path)
    val termName = TermName(name)
    val typeName = TypeName(name)

    params match {
      case Nil => ParamsObject(
        tree = q"case object $termName",
        tpe = tq"$termName.type"
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
    val termName = TermName(name)
    val typeName = TypeName(name)

    params match {
      case Nil => q"$termName"
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

    val (methodImplList, paramsObjects) = validMethods.collect { case (symbol, method) if symbol.isAbstract =>
      val path = t.methodPath(traitTag.tpe, symbol)
      val parameters =  t.paramsAsValDefs(method)
      val paramsObject = t.paramsAsObject(method, path)
      val paramListValue = t.newParamsObject(method, path)
      val innerReturnType = method.finalResultType.typeArgs.head

      (q"""
        override def ${symbol.name}(...$parameters): ${method.finalResultType} = {
          impl.execute[${paramsObject.tpe}, $innerReturnType]($path, $paramListValue)
        }
      """, paramsObject.tree)
    }.unzip
    val methodImpls = if (methodImplList.isEmpty) List(EmptyTree) else methodImplList

    q"""
      val impl = new ${t.internalPkg}.ClientImpl(${t.macroThis})

      ..$paramsObjects
      new ${traitTag.tpe.finalResultType} {
        ..$methodImpls
      }
    """
  }
}

object RouterMacro {
  import sloth.Router

  def impl[Trait, PickleType, Result[_]]
    (c: Context)
    (value: c.Expr[Trait])
    (functor: c.Expr[cats.Functor[Result]])
    (implicit traitTag: c.WeakTypeTag[Trait], pickleTypeTag: c.WeakTypeTag[PickleType], resultTag: c.WeakTypeTag[Result[_]]): c.Expr[Router[PickleType, Result]] = Translator(c) { t =>
    import c.universe._

    val validMethods = t.supportedMethodsInType(traitTag.tpe, resultTag.tpe)

    val (methodCases, paramsObjects) = validMethods.map { case (symbol, method) =>
      val path = t.methodPath(traitTag.tpe, symbol)
      val paramsObject = t.paramsAsObject(method, path)
      val argParams: List[List[Tree]] = t.objectToParams(method, TermName("args"))
      val innerReturnType = method.finalResultType.typeArgs.head

      (cq"""
        ${t.slothPkg}.Request($path, payload) =>
          impl.execute[${paramsObject.tpe}, $innerReturnType]($path, payload) { args =>
            value.${symbol.name.toTermName}(...$argParams)
          }
      """, paramsObject.tree)
    }.unzip

    q"""
      val value = $value
      val impl = new ${t.internalPkg}.RouterImpl[${pickleTypeTag.tpe}, ${resultTag.tpe.typeConstructor}]()($functor)

      ..$paramsObjects

      val current: ${t.slothPkg}.Router[${pickleTypeTag.tpe}, ${resultTag.tpe.typeConstructor}] = ${t.macroThis}
      current.orElse(new ${t.slothPkg}.Router[${pickleTypeTag.tpe}, ${resultTag.tpe.typeConstructor}] {
        override def apply(request: ${t.slothPkg}.Request[${pickleTypeTag.tpe}]) = request match {
          case ..$methodCases
          case other => ${t.slothPkg}.RouterResult.Failure(None, ${t.slothPkg}.ServerFailure.PathNotFound(other.path))
        }
      })

    """
  }
}
