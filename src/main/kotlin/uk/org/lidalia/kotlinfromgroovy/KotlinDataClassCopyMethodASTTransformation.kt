package uk.org.lidalia.kotlinfromgroovy

import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.AnnotatedNode
import org.codehaus.groovy.ast.ClassCodeExpressionTransformer
import org.codehaus.groovy.ast.ClassCodeVisitorSupport
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.expr.ArgumentListExpression
import org.codehaus.groovy.ast.expr.ArrayExpression
import org.codehaus.groovy.ast.expr.ClassExpression
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.ConstructorCallExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MapExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.NamedArgumentListExpression
import org.codehaus.groovy.ast.expr.SpreadExpression
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

      override fun visitMethod(node: MethodNode) {
        if (node.hasCompileStaticAnnotation()) return
        if (!methodNeedsTransformation(node)) return
        super.visitMethod(node)
      }

      override fun transform(expr: Expression?): Expression? {
        // Return calls on this/super unchanged — running super.transform
        // on them disrupts Groovy's static dispatch for private methods.
        if (expr is MethodCallExpression && isCallOnThis(expr)) {
          return expr
        }

        // Detect named args before super.transform, which may convert
        // NamedArgumentListExpression to plain MapExpression
        val precomputedInfo = when (expr) {
          is MethodCallExpression -> findNamedArgs(expr.arguments)
          is ConstructorCallExpression -> findNamedArgs(expr.arguments)
          else -> null
        }
        val withTransformedChildren = super.transform(expr)
        return when (withTransformedChildren) {
          is MethodCallExpression -> {
            // Only transform method calls with named args.
            // Positional-only calls are handled at runtime by KotlinAwareMetaClass,
            // avoiding bytecode bloat in large files.
            if (precomputedInfo != null) {
              transformMethodCall(withTransformedChildren, precomputedInfo)
                ?: withTransformedChildren
            } else {
              withTransformedChildren
            }
          }

          is ConstructorCallExpression -> {
            transformConstructorCall(withTransformedChildren, precomputedInfo)
              ?: withTransformedChildren
          }

          else -> {
            withTransformedChildren
          }
        }
      }
    }
    source.ast.classes
      .filter { !it.hasCompileStaticAnnotation() }
      .filter { classNode ->
        classNode.methods.any { !it.hasCompileStaticAnnotation() && methodNeedsTransformation(it) }
      }
      .forEach { transformer.visitClass(it) }
  }

  private fun AnnotatedNode.hasCompileStaticAnnotation(): Boolean =
    hasAnnotationNamed("groovy.transform.CompileStatic") ||
      hasAnnotationNamed("groovy.transform.TypeChecked")

  private fun AnnotatedNode.hasAnnotationNamed(name: String): Boolean =
    annotations.any { it.classNode.name == name }

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
      if (firstArg is MapExpression && firstArg.mapEntryExpressions.isNotEmpty()) {
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

  private fun transformMethodCall(
    expr: MethodCallExpression,
    precomputedInfo: NamedArgsInfo,
  ): Expression? {
    if (containsSpreadExpression(expr.arguments)) return null
    val methodConstant = expr.method as? ConstantExpression ?: return null
    val methodName = methodConstant.value as String

    return StaticMethodCallExpression(
      kotlinInteropClass,
      "callMethodWithNamedArgs",
      ArgumentListExpression(
        listOf(
          expr.objectExpression,
          ConstantExpression(methodName),
          precomputedInfo.namedArgMap,
          ArrayExpression(ClassHelper.OBJECT_TYPE, precomputedInfo.positionalExprs),
          ConstantExpression(precomputedInfo.namedFirst),
        ),
      ),
    )
  }

  private fun transformConstructorCall(
    expr: ConstructorCallExpression,
    precomputedInfo: NamedArgsInfo?,
  ): Expression? {
    if (expr.isSuperCall || expr.isThisCall) return null
    if (expr.isUsingAnonymousInnerClass) return null
    // Non-static inner classes have an implicit enclosing instance
    // parameter that constructWithNamedArgs cannot supply.
    if (expr.type.outerClass != null &&
      !java.lang.reflect.Modifier.isStatic(expr.type.modifiers)
    ) {
      return null
    }
    if (containsSpreadExpression(expr.arguments)) return null
    // Positional-only constructor calls on non-Kotlin classes don't need
    // transformation. Transforming them loses compile-time type info
    // (e.g. casts on null args), causing ambiguity with overloaded constructors.
    if (precomputedInfo == null && !isKotlinClassNode(expr.type)) return null
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

  private fun containsSpreadExpression(args: Expression): Boolean = when (args) {
    is TupleExpression -> args.expressions.any { it is SpreadExpression }
    else -> false
  }

  private fun isCallOnThis(expr: MethodCallExpression): Boolean {
    val obj = expr.objectExpression
    return obj is VariableExpression && (obj.name == "this" || obj.name == "super")
  }

  private fun extractPositionalArgs(args: Expression): List<Expression> = when (args) {
    is TupleExpression -> args.expressions.toList()
    else -> emptyList()
  }

  private fun isKotlinClassNode(classNode: ClassNode): Boolean =
    classNode.annotations.any { it.classNode.name == "kotlin.Metadata" }

  private fun methodNeedsTransformation(node: MethodNode): Boolean {
    var found = false
    val scanner = object : ClassCodeVisitorSupport() {
      override fun getSourceUnit(): SourceUnit? = null

      override fun visitConstructorCallExpression(call: ConstructorCallExpression) {
        found = true
      }

      override fun visitMethodCallExpression(call: MethodCallExpression) {
        if (!isCallOnThis(call)) {
          found = true
        }
        super.visitMethodCallExpression(call)
      }
    }
    node.code?.visit(scanner)
    return found
  }
}

private data class NamedArgsInfo(
  val namedArgMap: MapExpression,
  val positionalExprs: List<Expression>,
  val namedFirst: Boolean,
)
