package sloth.internal

import scala.quoted.*
import scala.annotation.experimental
import sloth.*
import scala.annotation.meta.param
import scala.NonEmptyTuple
import scala.quoted.runtime.StopMacroExpansion

private implicit val toExprEndpoint: ToExpr[Endpoint] = new ToExpr[Endpoint] {
  def apply(path: Endpoint)(using Quotes): Expr[Endpoint] = {
    import quotes.reflect._
    '{ Endpoint(${Expr(path.apiName)}, ${Expr(path.methodName)}) }
  }
}

private def getEndpointName(using Quotes)(symbol: quotes.reflect.Symbol): String = {
  import quotes.reflect.*

  symbol.annotations.collectFirst {
    case Apply(Select(New(annotation), _), Literal(constant) :: Nil) if annotation.tpe =:= TypeRepr.of[EndpointName] =>
      constant.value.asInstanceOf[String]
  }.getOrElse(symbol.name)
}

private def getTypeConstructor(using Quotes)(tpe: quotes.reflect.TypeRepr): quotes.reflect.TypeRepr = {
  import quotes.reflect.*

  tpe match {
    case tpe: AppliedType => getTypeConstructor(tpe.tycon)
    case tpe: LambdaType => getTypeConstructor(tpe.resType)
    case tpe => tpe
  }
}

private def getMethodType[Trait: Type](using Quotes)(method: quotes.reflect.Symbol): quotes.reflect.TypeRepr = {
  import quotes.reflect.*

  val traitType = TypeRepr.of[Trait]

  def recurse(tpe: TypeRepr): TypeRepr = tpe match {
    case MethodType(_, _, tpe) => recurse(tpe) // (multiple) parameter list are nested MethodTypes
    case ByNameType(tpe) => tpe // nullary methods
    case tpe => tpe // vals
  }

  recurse(traitType.memberType(method))
}

private def isExpectedReturnTypeConstructor[Trait: Type, Result[_]: Type](using Quotes)(method: quotes.reflect.Symbol): Boolean = {
  import quotes.reflect.*

  val expectedReturnType = TypeRepr.of[Result]
  val methodReturnType = getMethodType[Trait](method)

  getTypeConstructor(methodReturnType) <:< getTypeConstructor(expectedReturnType)
}

private def getInnerTypeOutOfReturnType[Trait: Type, Result[_]: Type](using Quotes)(method: quotes.reflect.Symbol): quotes.reflect.TypeRepr = {
  import quotes.reflect.*

  val expectedReturnType = TypeRepr.of[Result]

  val parameterTypeIndex = expectedReturnType match {
    case tpe: TypeLambda if tpe.paramTypes.nonEmpty =>
      val firstParamType = tpe.paramTypes.head
      tpe.typeArgs.indexWhere(_ =:= firstParamType)
    case _ => -1
  }

  val methodReturnType = getMethodType[Trait](method)
  parameterTypeIndex match {
    case -1 => methodReturnType.typeArgs.last
    case index => methodReturnType.typeArgs(index)
  }
}

def createTypeTreeTuple(using Quotes)(tupleTypesList: List[quotes.reflect.TypeRepr]): quotes.reflect.TypeRepr = {
  import quotes.reflect.*

  tupleTypesList match {
    case Nil => TypeRepr.of[EmptyTuple]
    case head :: tail => TypeRepr.of[*:].appliedTo(List(head, createTypeTreeTuple(tail)))
  }
}

private def checkMethodErrors[Trait: Type, Result[_]: Type](using q: Quotes)(methods: Seq[quotes.reflect.Symbol]): Unit = {
  import quotes.reflect.*

  val duplicateErrors = methods.groupBy(getEndpointName).collect { case (name, symbols) if symbols.size > 1 =>
    val message = s"Method $name is overloaded, please rename one of the methods or use the EndpointName annotation to disambiguate"
    (message, symbols.flatMap(_.pos).lastOption)
  }

  val invalidMethodErrors = methods.flatMap { method =>
    val isExpectedReturnType = isExpectedReturnTypeConstructor[Trait, Result](method)
    val hasGenericParams = method.paramSymss.headOption.exists(_.exists(_.isType))

    List(
      Option.when(hasGenericParams)(s"Method ${method.name} has a generic type parameter, this is not supported"),
      Option.when(!isExpectedReturnType)(s"Method ${method.name} has unexpected return type: is ${getTypeConstructor(getMethodType[Trait](method)).show}, but should be ${getTypeConstructor(TypeRepr.of[Result]).show}"),
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

  private def implBase[Trait: Type, PickleType: Type, Result[_]: Type](implInstance: Expr[Any], prefix: Expr[Client[PickleType, Result]])(using Quotes): Expr[Trait] = {
    import quotes.reflect.*

    val methods = definedMethodsInType[Trait]
    checkMethodErrors[Trait, Result](methods)

    val traitPathPart = getEndpointName(TypeRepr.of[Trait].typeSymbol)

    def decls(cls: Symbol): List[Symbol] = methods.map { method =>
      val methodType = TypeRepr.of[Trait].memberType(method)
      Symbol.newMethod(cls, method.name, methodType, flags = Flags.EmptyFlags /*TODO: method.flags */, privateWithin = method.privateWithin.fold(Symbol.noSymbol)(_.typeSymbol))
    }

    val parents = List(TypeTree.of[Object], TypeTree.of[Trait])
    val cls = Symbol.newClass(Symbol.spliceOwner, "Anon", parents.map(_.tpe), decls, selfType = None)

    val result = ValDef.let(Symbol.spliceOwner, implInstance.asTerm) { implRef =>
      val body = (cls.declaredMethods.zip(methods)).map { case (method, origMethod) =>
        val methodPathPart = getEndpointName(origMethod)
        val path = Endpoint(traitPathPart, methodPathPart)
        val pathExpr = Expr(path)

        DefDef(method, { argss =>
          // check argss and method.paramSyms have same length outside and inside
          val sameLength =
            argss.length == method.paramSymss.length &&
            argss.zip(method.paramSymss).forall { case (a,b) => a.length == b.length }

          Option.when(sameLength) {
            val tupleExpr = argss.flatten match {
              case Nil => '{()}
              case arg :: Nil => arg.asExpr
              case allArgs => Expr.ofTupleFromSeq(allArgs.map(_.asExpr))
            }

            val tupleTypesList = origMethod.paramSymss.flatten.map(_.tree.asInstanceOf[ValDef].tpt.tpe)
            val tupleType = tupleTypesList match {
              case Nil => TypeRepr.of[Unit]
              case head :: Nil => head
              case tupleTypesList => createTypeTreeTuple(tupleTypesList)
            }

            val returnType = getInnerTypeOutOfReturnType[Trait, Result](method)

            val clientImplType = TypeRepr.of[ClientImpl[PickleType, Result]].typeSymbol
            val tupleTypeTree = TypeTree.of(using tupleType.asType)
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
    checkMethodErrors[Trait, Result](methods)

    val traitPathPart = getEndpointName(TypeRepr.of[Trait].typeSymbol)

    type FunctionInput = PickleType
    type FunctionOutput = Either[ServerFailure, Result[PickleType]]

    val result = ValDef.let(Symbol.spliceOwner, implInstance.asTerm) { implRef =>
      def methodCases(endpointTerm: Term) = methods.map { method =>
        val methodPathPart = getEndpointName(method)
        val path = Endpoint(traitPathPart, methodPathPart)

        val returnType = getInnerTypeOutOfReturnType[Trait, Result](method)

        val tupleTypesList = method.paramSymss.flatten.map(_.tree.asInstanceOf[ValDef].tpt.tpe)
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
              List(endpointTerm, payloadArg.asExpr.asTerm)
            ),
            List(instanceLambda)
          )
        })

        val caseBody = '{ Some(${lambda.asExprOf[FunctionInput => FunctionOutput]}) }
        CaseDef(Literal(StringConstant(methodPathPart)), None, caseBody.asTerm)
      }

      '{
        ${prefix}.orElse { endpoint =>
          if (endpoint.apiName == ${Expr(traitPathPart)}) {
            ${Match(
              '{endpoint.methodName}.asTerm,
              methodCases('{endpoint}.asTerm) :+ CaseDef(Wildcard(), None, '{ None }.asTerm)
            ).asExprOf[Option[FunctionInput => FunctionOutput]]}
          } else None
        }
      }.asTerm
    }

    // println(result.show)
    result.asExprOf[Router[PickleType, Result]]
  }
}
