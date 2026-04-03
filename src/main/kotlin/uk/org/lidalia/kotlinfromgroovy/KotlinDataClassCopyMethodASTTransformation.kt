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
        // Detect named args before super.transform, which may convert
        // NamedArgumentListExpression to plain MapExpression
        val precomputedInfo = when {
          expr is MethodCallExpression && !isCallOnThis(expr) -> findNamedArgs(expr.arguments)
          expr is ConstructorCallExpression -> findNamedArgs(expr.arguments)
          else -> null
        }
        val withTransformedChildren = super.transform(expr)
        return when (withTransformedChildren) {
          is MethodCallExpression ->
            if (isCallOnThis(withTransformedChildren)) withTransformedChildren
            else transformMethodCall(withTransformedChildren, precomputedInfo) ?: withTransformedChildren
          is ConstructorCallExpression ->
            transformConstructorCall(withTransformedChildren, precomputedInfo) ?: withTransformedChildren
          else -> withTransformedChildren
        }
      }
    }
    source.ast.classes.forEach { transformer.visitClass(it) }
  }

  private fun findNamedArgs(args: Expression): NamedArgsInfo? {
    val exprs = when (args) {
      is TupleExpression -> args.expressions
      else -> return null
    }

    val named = exprs.filterIsInstance<NamedArgumentListExpression>().firstOrNull()
    val mapExpr: MapExpression?
    val positional: List<Expression>

    if (named != null) {
      mapExpr = MapExpression(named.mapEntryExpressions)
      positional = exprs.filter { it !is NamedArgumentListExpression }
    } else {
      val firstArg = exprs.firstOrNull()
      if (firstArg is MapExpression) {
        mapExpr = firstArg
        positional = exprs.drop(1)
      } else {
        return null
      }
    }

    val namedFirst = detectNamedFirst(mapExpr, positional)
    return NamedArgsInfo(mapExpr, positional, namedFirst)
  }

  private fun detectNamedFirst(mapExpr: MapExpression, positional: List<Expression>): Boolean {
    if (positional.isEmpty() || mapExpr.mapEntryExpressions.isEmpty()) return false
    val firstNamedLine = mapExpr.mapEntryExpressions.first().lineNumber
    val firstNamedCol = mapExpr.mapEntryExpressions.first().columnNumber
    val firstPositionalLine = positional.first().lineNumber
    val firstPositionalCol = positional.first().columnNumber
    return firstNamedLine < firstPositionalLine ||
      (firstNamedLine == firstPositionalLine && firstNamedCol < firstPositionalCol)
  }

  private fun transformMethodCall(expr: MethodCallExpression, precomputedInfo: NamedArgsInfo?): Expression? {
    val methodConstant = expr.method as? ConstantExpression ?: return null
    val methodName = methodConstant.value as String
    val namedArgMap = precomputedInfo?.namedArgMap ?: MapExpression()
    val positionalExprs = precomputedInfo?.positionalExprs ?: extractPositionalArgs(expr.arguments)
    val namedFirst = precomputedInfo?.namedFirst ?: false

    return StaticMethodCallExpression(
      kotlinInteropClass,
      "callMethodWithNamedArgs",
      ArgumentListExpression(
        listOf(
          expr.objectExpression,
          ConstantExpression(methodName),
          namedArgMap,
          ArrayExpression(ClassHelper.OBJECT_TYPE, positionalExprs),
          ConstantExpression(namedFirst),
        ),
      ),
    )
  }

  private fun transformConstructorCall(expr: ConstructorCallExpression, precomputedInfo: NamedArgsInfo?): Expression? {
    val namedArgMap = precomputedInfo?.namedArgMap ?: MapExpression()
    val positionalExprs = precomputedInfo?.positionalExprs ?: extractPositionalArgs(expr.arguments)
    val namedFirst = precomputedInfo?.namedFirst ?: false

    return StaticMethodCallExpression(
      kotlinInteropClass,
      "constructWithNamedArgs",
      ArgumentListExpression(
        listOf(
          ClassExpression(expr.type),
          namedArgMap,
          ArrayExpression(ClassHelper.OBJECT_TYPE, positionalExprs),
          ConstantExpression(namedFirst),
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
  val namedFirst: Boolean,
)
