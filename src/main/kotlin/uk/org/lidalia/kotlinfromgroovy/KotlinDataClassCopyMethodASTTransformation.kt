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
import java.lang.reflect.Modifier

private val kotlinInteropClass = ClassHelper.make("uk.org.lidalia.kotlinfromgroovy.KotlinInterop")
private val javaLangClassNode = ClassHelper.make(Class::class.java)

@GroovyASTTransformation(phase = CompilePhase.INSTRUCTION_SELECTION)
class KotlinDataClassCopyMethodASTTransformation : AbstractASTTransformation() {

  override fun visit(nodes: Array<ASTNode>, source: SourceUnit) {
    val extensionScope = buildExtensionScope(source)

    val transformer = object : ClassCodeExpressionTransformer() {
      override fun getSourceUnit(): SourceUnit = source

      override fun visitMethod(node: MethodNode) {
        if (node.hasCompileStaticAnnotation()) return
        if (!methodNeedsTransformation(node, extensionScope)) return
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
            // Try extension function rewrite first (handles both positional and named args)
            transformExtensionCall(withTransformedChildren, precomputedInfo, extensionScope)
              ?: if (precomputedInfo != null) {
                // Non-extension calls with named args go through callMethodWithNamedArgs
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
        classNode.methods.any {
          !it.hasCompileStaticAnnotation() &&
            methodNeedsTransformation(it, extensionScope)
        }
      }
      .forEach { transformer.visitClass(it) }
  }

  private fun buildExtensionScope(source: SourceUnit): Map<String, List<String>> {
    val classLoader = source.classLoader ?: return emptyMap()
    val callerPackage = source.ast.packageName?.removeSuffix(".") ?: ""
    val result = mutableMapOf<String, MutableList<String>>()

    // Explicit static imports: import static FooKt.methodName
    // Only the specifically named method is in scope, not all methods from the class.
    source.ast.staticImports.forEach { (methodName, importNode) ->
      val className = importNode.type?.name ?: return@forEach
      result.getOrPut(methodName) { mutableListOf() }.add(className)
    }

    // Static star imports: import static FooKt.*
    // All public static methods from the class are in scope.
    val staticStarImportClassNames = source.ast.staticStarImports.values.mapNotNull {
      it.type?.name
    }
    staticStarImportClassNames.toSet().forEach { className ->
      collectExtensionsFromClass(className, classLoader, result)
    }

    // Check star-imported packages
    val starImportPackages = source.ast.starImports
      .mapNotNull { it.packageName?.removeSuffix(".") }
    starImportPackages.forEach { pkg ->
      collectExtensionsFromPackage(pkg, classLoader, result)
    }

    // Check same package
    if (callerPackage.isNotEmpty()) {
      collectExtensionsFromPackage(callerPackage, classLoader, result)
    }

    return result
  }

  private fun collectExtensionsFromClass(
    className: String,
    classLoader: ClassLoader,
    result: MutableMap<String, MutableList<String>>,
  ) {
    try {
      val clazz = classLoader.loadClass(className)
      if (!isKotlinFileFacade(clazz)) return

      clazz.declaredMethods.asSequence()
        .filter { Modifier.isPublic(it.modifiers) }
        .filter { Modifier.isStatic(it.modifiers) }
        .filter { !it.isSynthetic }
        .forEach { method ->
          result.getOrPut(method.name) { mutableListOf() }.add(className)
        }
    } catch (_: Exception) {
      // Skip classes we can't load — e.g. Groovy classes not yet compiled
    }
  }

  private fun isKotlinFileFacade(clazz: Class<*>): Boolean {
    val metadata = clazz.annotations
      .find { it.annotationClass.qualifiedName == "kotlin.Metadata" }
      ?: return false
    return try {
      val kMethod = metadata.annotationClass.java.getMethod("k")
      kMethod.invoke(metadata) == 2
    } catch (_: Exception) {
      false
    }
  }

  private fun collectExtensionsFromPackage(
    packageName: String,
    classLoader: ClassLoader,
    result: MutableMap<String, MutableList<String>>,
  ) {
    val packagePath = packageName.replace('.', '/')
    try {
      classLoader.getResources(packagePath).asSequence().forEach { url ->
        try {
          val dir = java.io.File(java.net.URI(url.toString().replace(" ", "%20")))
          if (dir.isDirectory) {
            dir.listFiles()
              ?.filter { it.name.endsWith("Kt.class") && '$' !in it.name }
              ?.forEach { file ->
                val className = "$packageName.${file.nameWithoutExtension}"
                collectExtensionsFromClass(className, classLoader, result)
              }
          }
        } catch (_: Exception) {
          // Skip resources we can't process
        }
      }
    } catch (_: Exception) {
      // Skip packages we can't scan
    }
  }

  private fun transformExtensionCall(
    expr: MethodCallExpression,
    precomputedInfo: NamedArgsInfo?,
    extensionScope: Map<String, List<String>>,
  ): Expression? {
    if (isCallOnThis(expr)) return null
    if (containsSpreadExpression(expr.arguments)) return null
    val methodName = extractMethodName(expr) ?: return null

    val declaringClassNames = extensionScope[methodName] ?: return null

    val namedArgMap = precomputedInfo?.namedArgMap ?: MapExpression()
    val positionalExprs = precomputedInfo?.positionalExprs ?: extractPositionalArgs(expr.arguments)
    val namedFirst = precomputedInfo?.namedFirst ?: false

    return StaticMethodCallExpression(
      kotlinInteropClass,
      "callExtensionMethod",
      ArgumentListExpression(
        listOf(
          createClassArray(declaringClassNames),
          ConstantExpression(methodName),
          expr.objectExpression,
          namedArgMap,
          ArrayExpression(ClassHelper.OBJECT_TYPE, positionalExprs),
          ConstantExpression(namedFirst),
        ),
      ),
    )
  }

  private fun createClassArray(classNames: List<String>): ArrayExpression = ArrayExpression(
    javaLangClassNode,
    classNames.map { ClassExpression(ClassHelper.make(it)) },
  )

  private fun AnnotatedNode.hasCompileStaticAnnotation(): Boolean =
    hasAnnotationNamed("groovy.transform.CompileStatic") ||
      hasAnnotationNamed("groovy.transform.TypeChecked")

  private fun AnnotatedNode.hasAnnotationNamed(name: String): Boolean =
    annotations.any { it.classNode.name == name }

  private fun findNamedArgs(args: Expression): NamedArgsInfo? =
    (args as? TupleExpression)?.expressions?.let { exprs ->
      val named = exprs.filterIsInstance<NamedArgumentListExpression>().firstOrNull()
      when {
        named != null -> {
          val mapExpr = MapExpression(named.mapEntryExpressions)
          val positional = exprs.filter { it !is NamedArgumentListExpression }
          NamedArgsInfo(mapExpr, positional, detectNamedFirst(mapExpr, positional))
        }

        else -> {
          val firstArg = exprs.firstOrNull()
          if (firstArg is MapExpression && firstArg.mapEntryExpressions.isNotEmpty()) {
            val positional = exprs.drop(1)
            NamedArgsInfo(firstArg, positional, detectNamedFirst(firstArg, positional))
          } else {
            null
          }
        }
      }
    }

  private fun detectNamedFirst(mapExpr: MapExpression, positional: List<Expression>): Boolean {
    val firstNamed = mapExpr.mapEntryExpressions.firstOrNull()
    val firstPos = positional.firstOrNull()
    return when {
      firstNamed == null || firstPos == null -> false
      firstNamed.lineNumber != firstPos.lineNumber -> firstNamed.lineNumber < firstPos.lineNumber
      else -> firstNamed.columnNumber < firstPos.columnNumber
    }
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
      !Modifier.isStatic(expr.type.modifiers)
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

  /**
   * Extract the method name from a MethodCallExpression. Handles both plain
   * ConstantExpression method names and Spock's value-recorder wrapping where
   * the method name is passed through $spock_valueRecorder.record(index, name).
   */
  private fun extractMethodName(expr: MethodCallExpression): String? {
    val method = expr.method
    if (method is ConstantExpression) {
      return method.value as? String
    }
    if (method is MethodCallExpression) {
      val args = method.arguments
      if (args is ArgumentListExpression && args.expressions.size == 2) {
        val nameArg = args.expressions[1]
        if (nameArg is ConstantExpression) {
          return nameArg.value as? String
        }
      }
    }
    return null
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

  private fun methodNeedsTransformation(
    node: MethodNode,
    extensionScope: Map<String, List<String>>,
  ): Boolean {
    var found = false
    val scanner = object : ClassCodeVisitorSupport() {
      override fun getSourceUnit(): SourceUnit? = null

      override fun visitConstructorCallExpression(call: ConstructorCallExpression) {
        found = true
      }

      override fun visitMethodCallExpression(call: MethodCallExpression) {
        if (!isCallOnThis(call)) {
          found = true
        } else {
          // Even this/super calls need transformation if they match extension functions
          val methodConstant = call.method as? ConstantExpression
          val methodName = methodConstant?.value as? String
          if (methodName != null && methodName in extensionScope) {
            found = true
          }
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
