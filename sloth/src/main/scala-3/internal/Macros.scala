package sloth.internal

import scala.quoted.*
import scala.annotation.experimental
import sloth.*
import scala.annotation.meta.param
import scala.NonEmptyTuple
import scala.quoted.runtime.StopMacroExpansion

private def getPathName(using Quotes)(symbol: quotes.reflect.Symbol): String = {
  import quotes.reflect.*

  symbol.annotations.collectFirst {
    case Apply(Select(New(annotation), _), Literal(constant) :: Nil) if annotation.tpe =:= TypeRepr.of[PathName] =>
      constant.value.asInstanceOf[String]
  }.getOrElse(symbol.name)
}

private def isExpectedReturnTypeConstructor[Result[_]: Type](using Quotes)(method: quotes.reflect.Symbol): Boolean = {
  // import quotes.reflect.*

  // val apiType = TypeRepr.of[Any]
  // val expectedReturnType = TypeRepr.of[Result]

  // method.tree match {
  //   case DefDef(_,_,typedTree,_) =>
  //     val methodReturnType = TypeRepr.of(using typedTree.tpe.asType)
      
  //     methodReturnType match {
  //       case tpe: AppliedType =>
  //         tpe.tycon.dealias <:< expectedReturnType.dealias
  //       case _ => false
  //     }
  //   case _ => false
  // }

  //TODO: the above does not work if the return type is a generic type parameter (see SlothSpec).
  //It will anyhow give a compile error on the generated code.
  //It would just be nicer to show the user which method has the wrong signature.
  true
}

private def getInnerTypeOutOfReturnType[Result[_]: Type](using Quotes)(method: quotes.reflect.Symbol): quotes.reflect.TypeRepr = {
  import quotes.reflect.*

  val expectedReturnType = TypeRepr.of[Result]

  val parameterTypeIndex = expectedReturnType match {
    case tpe: TypeLambda if tpe.paramTypes.nonEmpty =>
      val firstParamType = tpe.paramTypes.head
      tpe.typeArgs.indexWhere(_ =:= firstParamType)
    case _ => -1
  }
 
  method.tree match {
    case DefDef(_,_,typedTree,_) =>
      val methodReturnType = TypeRepr.of(using typedTree.tpe.asType)
      parameterTypeIndex match {
        case -1 => methodReturnType.typeArgs.last
        case index =>  methodReturnType.typeArgs(index)
      }
    case _ =>
      report.errorAndAbort("Method is not a DefDef: ${method.show}. This is a bug, please report at https://github.com/cornerman/sloth.", method.tree.pos)
  }
}

private def checkMethodErrors[Result[_]: Type](using q: Quotes)(methods: Seq[quotes.reflect.Symbol]): Unit = {
  import quotes.reflect.*

  val duplicateErrors = methods.groupBy(m => getPathName(m)).collect { case (name, symbols) if symbols.size > 1 =>
    val message = s"Method $name is overloaded, please rename one of the methods or use the PathName annotation to disambiguate"
    (message, symbols.flatMap(_.pos).lastOption)
  }

  val invalidMethodErrors = methods.flatMap { method =>
    val isExpectedReturnType = isExpectedReturnTypeConstructor[Result](method)
    val hasGenericParams = method.paramSymss.headOption.exists(_.exists(_.isType))

    List(
      Option.when(hasGenericParams)(s"Method ${method.name} has a generic type parameter, this is not supported"),
      Option.when(!isExpectedReturnType)(s"Method ${method.name} has unexpected return type, should be ${TypeRepr.of[Result].typeSymbol.name}"),
    ).flatten.map(_ -> method.pos)
  }

  val errors = duplicateErrors ++ invalidMethodErrors

  if (errors.nonEmpty) {
    errors.foreach { (message, pos) =>
      report.error(message, pos.getOrElse(Position.ofMacroExpansion))
    }

    throw StopMacroExpansion()
  }
}

private def definedMethodsInType[T: Type](using Quotes): List[quotes.reflect.Symbol] = {
  import quotes.reflect.*

  val typeSymbol = TypeRepr.of[T].typeSymbol

  for {
    member <- typeSymbol.methodMembers
    //is abstract method, not implemented
    if member.flags.is(Flags.Deferred)

    // TODO: is that public?
    // TODO? if member.privateWithin
    if !member.flags.is(Flags.Private)
    if !member.flags.is(Flags.Protected)
    if !member.flags.is(Flags.PrivateLocal)

    if !member.isClassConstructor
    if !member.flags.is(Flags.Synthetic)
  } yield {
    member
  }
}

@experimental
object TraitMacro {

  def impl[Trait: Type, PickleType: Type, Result[_]: Type](prefix: Expr[ClientCo[PickleType, Result]])(using Quotes): Expr[Trait] = {
    val implInstance = '{ new ClientImpl[PickleType, Result](${prefix}) }
    implBase[Trait, PickleType, Result](implInstance, prefix)
  }

  def implContra[Trait: Type, PickleType: Type, Result[_]: Type](prefix: Expr[ClientContra[PickleType, Result]])(using Quotes): Expr[Trait] = {
    val implInstance = '{ new ClientContraImpl[PickleType, Result](${prefix}) }
    implBase[Trait, PickleType, Result](implInstance, prefix)
  }

  private def implBase[Trait: Type, PickleType: Type, Result[_]: Type](implInstance: Expr[Any], prefix: Expr[Any])(using Quotes): Expr[Trait] = {
    import quotes.reflect.*

    val methods = definedMethodsInType[Trait]
    checkMethodErrors[Result](methods)

    val traitPathPart = getPathName(TypeRepr.of[Trait].typeSymbol)

    def decls(cls: Symbol): List[Symbol] = methods.map { method =>
      //TODO: use: https://github.com/lampepfl/dotty/pull/15024
      method.tree.changeOwner(cls) match {
        case DefDef(name, clauses, typedTree,_) =>
          val tpeRepr = TypeRepr.of(using typedTree.tpe.asType)

          val methodType = if (clauses.isEmpty) {
            // nullary methods
            ByNameType(tpeRepr)
          } else {
            // non-nullary methods with parameters
            clauses.foldRight[TypeRepr](tpeRepr) { (clause, tpe) =>
              val names = clause.params.collect { case v: ValDef => v.name }
              val tpes = clause.params.collect { case v: ValDef => v.tpt.tpe }

              MethodType(names)(_ => tpes, _ => tpe)
            }
          }

          Symbol.newMethod(cls, name, methodType, flags = Flags.EmptyFlags /*TODO: method.flags */, privateWithin = method.privateWithin.fold(Symbol.noSymbol)(_.typeSymbol))
        case _ =>
          report.errorAndAbort(s"Cannot detect type of method: ${method.name}", method.tree.pos)
      }
    }

    val parents = List(TypeTree.of[Object], TypeTree.of[Trait])
    val cls = Symbol.newClass(Symbol.spliceOwner, "Anon", parents.map(_.tpe), decls, selfType = None)

    val result = ValDef.let(Symbol.spliceOwner, implInstance.asTerm) { implRef =>
      val body = cls.declaredMethods.map { method =>
        val methodPathPart = getPathName(method)
        val path = traitPathPart :: methodPathPart :: Nil

        DefDef(method, { argss =>
          // check argss and method.paramSyms have same length outside and inside
          val sameLength =
            argss.length == method.paramSymss.length &&
            argss.zip(method.paramSymss).forall { case (a,b) => a.length == b.length }

          Option.when(sameLength) {
            val pathExpr = Expr(path)
            val tupleExpr = argss.flatten match {
              case Nil => '{()}
              case arg :: Nil => arg.asExpr
              case allArgs => Expr.ofTupleFromSeq(allArgs.map(_.asExpr))
            }
              
            val returnType = getInnerTypeOutOfReturnType[Result](method)

            val clientImplType = TypeRepr.of[ClientImpl[PickleType, Result]].typeSymbol
            val tupleTypeTree = TypeTree.of(using tupleExpr.asTerm.tpe.asType)
            val returnTypeTree = TypeTree.of(using returnType.asType)

            Apply(
              TypeApply(
                Select(implRef, clientImplType.declaredMethod("execute").head),
                List(tupleTypeTree, returnTypeTree)
              ),
              List(pathExpr.asTerm, tupleExpr.asTerm)
            )
          }
        })
      }

      val clsDef = ClassDef(cls, parents, body = body)

      val newCls = Typed(
        Apply(
          Select(New(TypeIdent(cls)), cls.primaryConstructor),
          Nil
        ),
        TypeTree.of[Trait]
      )

      Block(List(clsDef), newCls)
    }

    // println(result.show)
    result.asExprOf[Trait]
  }
}

object RouterMacro {

  def impl[Trait: Type, PickleType : Type, Result[_]: Type](prefix: Expr[RouterCo[PickleType, Result]], instance: Expr[Trait])(using Quotes): Expr[RouterCo[PickleType, Result]] = {
    val implInstance = '{ new RouterImpl[PickleType, Result](${prefix}) }
    implBase[Trait, PickleType, Result](implInstance, prefix, instance).asExprOf[RouterCo[PickleType, Result]]
  }

  def implContra[Trait: Type, PickleType : Type, Result[_]: Type](prefix: Expr[RouterContra[PickleType, Result]], instance: Expr[Trait])(using Quotes): Expr[RouterContra[PickleType, Result]] = {
    val implInstance = '{ new RouterContraImpl[PickleType, Result](${prefix}) }
    implBase[Trait, PickleType, Result](implInstance, prefix, instance).asExprOf[RouterContra[PickleType, Result]]
  }

  private def implBase[Trait: Type, PickleType : Type, Result[_]: Type](implInstance: Expr[Any], prefix: Expr[Router[PickleType, Result]], instance: Expr[Trait])(using Quotes): Expr[Router[PickleType, Result]] = {
    import quotes.reflect.*

    val methods = definedMethodsInType[Trait]
    checkMethodErrors[Result](methods)

    val traitPathPart = getPathName(TypeRepr.of[Trait].typeSymbol)

    type FunctionInput = PickleType
    type FunctionOutput = Either[ServerFailure, Result[PickleType]]

    val result = ValDef.let(Symbol.spliceOwner, implInstance.asTerm) { implRef =>
      val methodDefinitions = methods.map { method =>
        val methodPathPart = getPathName(method)
        val path = traitPathPart :: methodPathPart :: Nil

        val pathExpr = Expr(path)
        val tupleTypesList = method.paramSymss.flatten.map(_.tree.asInstanceOf[ValDef].tpt.tpe)
        
        def createTypeTreeTuple(tupleTypesList: List[TypeRepr]): TypeRepr = tupleTypesList match {
          case Nil => TypeRepr.of[EmptyTuple]
          case head :: tail => TypeRepr.of[*:].appliedTo(List(head, createTypeTreeTuple(tail)))
        }
        
        val returnType = getInnerTypeOutOfReturnType[Result](method)
        val tupleType = tupleTypesList match {
          case Nil => TypeRepr.of[Unit]
          case head :: Nil => head
          case tupleTypesList => createTypeTreeTuple(tupleTypesList)
        }

        val routerImplType = TypeRepr.of[RouterImpl[PickleType, Result]].typeSymbol
        val tupleTypeTree = TypeTree.of(using tupleType.asType)
        val returnTypeTree = TypeTree.of(using returnType.asType)

        val lambdaType = MethodType(List("payload"))(
        _ => List(TypeRepr.of[FunctionInput]),
        _ => TypeRepr.of[FunctionOutput],
        )

        val lambda = Lambda(Symbol.spliceOwner, lambdaType, { (_, args) =>
          val payloadArg = args.head

          val instanceLambdaType = MethodType(List("tuple"))(
          _ => List(tupleTypeTree.tpe),
          _ => TypeRepr.of[Result].appliedTo(returnTypeTree.tpe),
          )

          val instanceLambda = Lambda(Symbol.spliceOwner, instanceLambdaType, { (_, instanceArgs) =>
            val tupleArg = instanceArgs.head
            val methodSelect = Select(instance.asTerm, method)

            val allParamCount = method.paramSymss.flatten.length
            var counter = 0

            method.paramSymss.foldLeft[Term](methodSelect) { (base, paramList) =>
              Apply(base, paramList.map { param =>
                counter += 1
                val paramType = param.tree.asInstanceOf[ValDef].tpt
                val paramExpr =
                  if (allParamCount == 1) tupleArg.asExpr
                  else '{ ${tupleArg.asExprOf[Tuple]}.productElement(${Expr(counter - 1)}) }

                Typed(paramExpr.asTerm, paramType)
              })
            }
          })

          Apply(
            Apply(
              TypeApply(
                Select(implRef, routerImplType.declaredMethod("execute").head),
                List(tupleTypeTree, returnTypeTree)
              ),
              List(pathExpr.asTerm, payloadArg.asExpr.asTerm)
            ),
            List(instanceLambda)
          )
        })

        '{
          (${Expr(methodPathPart)}, ${lambda.asExprOf[FunctionInput => FunctionOutput]})
        }
      }

      '{
        ${prefix}.orElse(${Expr(traitPathPart)}, Map.from(${Expr.ofList(methodDefinitions)}))
      }.asTerm
    }

    // println(result.show)
    result.asExprOf[Router[PickleType, Result]]
  }
}
