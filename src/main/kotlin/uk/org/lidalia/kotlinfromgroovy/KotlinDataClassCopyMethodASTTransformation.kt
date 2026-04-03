package uk.org.lidalia.kotlinfromgroovy

import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ClassCodeExpressionTransformer
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.expr.ArgumentListExpression
import org.codehaus.groovy.ast.expr.ArrayExpression
import org.codehaus.groovy.ast.expr.ClassExpression
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.ConstructorCallExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MapExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.NamedArgumentListExpression
import org.codehaus.groovy.ast.expr.StaticMethodCallExpression
import org.codehaus.groovy.ast.expr.TupleExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.transform.AbstractASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformation

private val kotlinInteropClass = ClassHelper.make("uk.org.lidalia.kotlinfromgroovy.KotlinInterop")

@GroovyASTTransformation(phase = CompilePhase.INSTRUCTION_SELECTION)
class KotlinDataClassCopyMethodASTTransformation : AbstractASTTransformation() {

  override fun visit(nodes: Array<ASTNode>, source: SourceUnit) {
    val transformer = object : ClassCodeExpressionTransformer() {
      override fun getSourceUnit(): SourceUnit = source

      override fun transform(expr: Expression?): Expression? {
        val withTransformedChildren = super.transform(expr)
        return when (withTransformedChildren) {
          is MethodCallExpression ->
            if (isCallOnThis(withTransformedChildren)) withTransformedChildren
            else transformMethodCall(withTransformedChildren) ?: withTransformedChildren
          is ConstructorCallExpression -> transformConstructorCall(withTransformedChildren) ?: withTransformedChildren
          else -> withTransformedChildren
        }
      }
    }
    source.ast.classes.forEach { transformer.visitClass(it) }
  }

  private fun findNamedArgs(args: Expression): NamedArgsInfo? =
    when (args) {
      is ArgumentListExpression -> {
        val named = args.expressions.filterIsInstance<NamedArgumentListExpression>().firstOrNull()
        if (named != null) {
          val positional = args.expressions.filter { it !is NamedArgumentListExpression }
          NamedArgsInfo(MapExpression(named.mapEntryExpressions), positional)
        } else {
          val firstArg = args.expressions.firstOrNull()
          if (firstArg is MapExpression && args.expressions.size > 1) {
            NamedArgsInfo(firstArg, args.expressions.drop(1))
          } else {
            null
          }
        }
      }
      is TupleExpression -> {
        val named = args.expressions.filterIsInstance<NamedArgumentListExpression>().firstOrNull()
        if (named != null) {
          val positional = args.expressions.filter { it !is NamedArgumentListExpression }
          NamedArgsInfo(MapExpression(named.mapEntryExpressions), positional)
        } else {
          null
        }
      }
      else -> null
    }

  private fun transformMethodCall(expr: MethodCallExpression): Expression? {
    val methodConstant = expr.method as? ConstantExpression ?: return null
    val methodName = methodConstant.value as String
    val info = findNamedArgs(expr.arguments)
    val namedArgMap = info?.namedArgMap ?: MapExpression()
    val positionalExprs = info?.positionalExprs ?: extractPositionalArgs(expr.arguments)

    return StaticMethodCallExpression(
      kotlinInteropClass,
      "callMethodWithNamedArgs",
      ArgumentListExpression(
        listOf(
          expr.objectExpression,
          ConstantExpression(methodName),
          namedArgMap,
          ArrayExpression(ClassHelper.OBJECT_TYPE, positionalExprs),
        ),
      ),
    )
  }

  private fun transformConstructorCall(expr: ConstructorCallExpression): Expression? {
    val info = findNamedArgs(expr.arguments)
    val namedArgMap = info?.namedArgMap ?: MapExpression()
    val positionalExprs = info?.positionalExprs ?: extractPositionalArgs(expr.arguments)

    return StaticMethodCallExpression(
      kotlinInteropClass,
      "constructWithNamedArgs",
      ArgumentListExpression(
        listOf(
          ClassExpression(expr.type),
          namedArgMap,
          ArrayExpression(ClassHelper.OBJECT_TYPE, positionalExprs),
        ),
      ),
    )
  }

  private fun isCallOnThis(expr: MethodCallExpression): Boolean {
    val obj = expr.objectExpression
    return obj is VariableExpression && (obj.name == "this" || obj.name == "super")
  }

  private fun extractPositionalArgs(args: Expression): List<Expression> =
    when (args) {
      is TupleExpression -> args.expressions.toList()
      else -> emptyList()
    }
}

private data class NamedArgsInfo(
  val namedArgMap: MapExpression,
  val positionalExprs: List<Expression>,
)
