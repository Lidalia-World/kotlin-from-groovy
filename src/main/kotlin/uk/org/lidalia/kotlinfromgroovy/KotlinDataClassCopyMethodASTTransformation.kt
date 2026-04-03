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
        val transformed = when {
          expr is MethodCallExpression && hasNamedArgs(expr) ->
            transformMethodCall(expr)
          expr is ConstructorCallExpression && hasNamedArgs(expr) ->
            transformConstructorCall(expr)
          else -> null
        }
        return transformed ?: super.transform(expr)
      }
    }
    source.ast.classes.forEach { transformer.visitClass(it) }
  }

  private fun hasNamedArgs(expr: MethodCallExpression): Boolean {
    val args = expr.arguments as? TupleExpression ?: return false
    return args.expressions.any { it is NamedArgumentListExpression }
  }

  private fun hasNamedArgs(expr: ConstructorCallExpression): Boolean {
    val args = expr.arguments as? TupleExpression ?: return false
    return args.expressions.any { it is NamedArgumentListExpression }
  }

  private fun transformMethodCall(expr: MethodCallExpression): Expression? {
    val methodConstant = expr.method as? ConstantExpression ?: return null
    val (namedArgMap, positionalArgs) = extractArgs(expr.arguments as TupleExpression)
    val methodName = methodConstant.value as String

    return StaticMethodCallExpression(
      kotlinInteropClass,
      "callMethodWithNamedArgs",
      ArgumentListExpression(
        listOf(
          expr.objectExpression,
          ConstantExpression(methodName),
          namedArgMap,
          positionalArgs,
        ),
      ),
    )
  }

  private fun transformConstructorCall(expr: ConstructorCallExpression): Expression {
    val (namedArgMap, positionalArgs) = extractArgs(expr.arguments as TupleExpression)

    return StaticMethodCallExpression(
      kotlinInteropClass,
      "constructWithNamedArgs",
      ArgumentListExpression(
        listOf(
          ClassExpression(expr.type),
          namedArgMap,
          positionalArgs,
        ),
      ),
    )
  }

  private fun extractArgs(tuple: TupleExpression): Pair<MapExpression, ArrayExpression> {
    val namedArgList = tuple.expressions.filterIsInstance<NamedArgumentListExpression>().firstOrNull()
    val positionalExprs = tuple.expressions.filter { it !is NamedArgumentListExpression }

    val mapExpr = if (namedArgList != null) {
      MapExpression(namedArgList.mapEntryExpressions)
    } else {
      MapExpression()
    }

    val objectClassNode = ClassHelper.OBJECT_TYPE
    val arrayExpr = ArrayExpression(objectClassNode, positionalExprs)

    return mapExpr to arrayExpr
  }
}
