@file:OptIn(ExperimentalContracts::class)

package uk.org.lidalia.kotlinfromgroovy

import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ClassCodeExpressionTransformer
import org.codehaus.groovy.ast.expr.DeclarationExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.NamedArgumentListExpression
import org.codehaus.groovy.ast.expr.TupleExpression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.ExpressionStatement
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.transform.AbstractASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformation
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

@GroovyASTTransformation(phase = CompilePhase.INSTRUCTION_SELECTION)
class KotlinDataClassCopyMethodASTTransformation : AbstractASTTransformation() {

  override fun visit(nodes: Array<ASTNode>, source: SourceUnit) {
    val trn = object : ClassCodeExpressionTransformer() {
      override fun getSourceUnit(): SourceUnit = source

      override fun transform(expr: Expression?): Expression? =
        if (isNamedArgumentMethodCall(expr)) {
          println()
          println("Expression: $expr")
          println("Expression.text: ${expr.text}")
          println("Expression.isImplicitThis: ${expr.isImplicitThis}")
          println("Expression.objectExpression: ${expr.objectExpression}")
          println("Expression.methodTarget: ${expr.methodTarget}")
          println("Expression.method: ${expr.method}")
          println("Expression.declaringClass: ${expr.declaringClass}")
          println("Expression.instance: ${expr.instance}")
          println("Expression.type: ${expr.type}")
          if (!expr.isImplicitThis) {
//                        val target = expr.objectExpression as? VariableExpression
//                        println(expr.objectExpression)
          }
          super.transform(expr)
        } else {
          super.transform(expr)
        }
    }
    source.ast.methods.forEach { trn.visitMethod(it) }
    source.ast.classes.forEach { trn.visitClass(it) }
  }

  private fun isNamedArgumentMethodCall(expression: Expression?): Boolean {
    contract {
      returns(true) implies (expression is MethodCallExpression)
    }
    return when (expression) {
      is MethodCallExpression -> {
        tupleExpression(expression) is NamedArgumentListExpression
      }

      else -> {
        false
      }
    }
  }

  private fun tupleExpression(expression: MethodCallExpression): Expression? =
    (expression.arguments as? TupleExpression)?.expressions?.singleOrNull()

  private fun SourceUnit.findNamedArgumentMethodCalls(): List<MethodCallExpression> = ast.classes
    .map { classNode ->

      val fieldInitialiseMethodCalls = classNode.fields.mapNotNull { fieldNode ->
        if (fieldNode.hasInitialExpression()) {
          val initialExpression = fieldNode.initialExpression
          if (initialExpression is MethodCallExpression) {
            filterMethodCallExpression(initialExpression)
          } else {
            null
          }
        } else {
          null
        }
      }

      val inMethodCalls = classNode.methods
        .mapNotNull { methodNode ->
          val code = methodNode.code
          if (code is BlockStatement) {
            code.statements
              .filterIsInstance<ExpressionStatement>()
              .mapNotNull { expressionStatement ->
                when (val expression = expressionStatement.expression) {
                  is MethodCallExpression -> {
                    filterMethodCallExpression(expression)
                  }

                  is DeclarationExpression -> {
                    when (val rightExpression = expression.rightExpression) {
                      is MethodCallExpression -> filterMethodCallExpression(
                        rightExpression,
                      )

                      else -> null
                    }
                  }

                  else -> {
                    null
                  }
                }
              }
          } else {
            null
          }
        }.flatten()

      fieldInitialiseMethodCalls + inMethodCalls
    }.flatten()

  private fun filterMethodCallExpression(
    initialExpression: MethodCallExpression,
  ): MethodCallExpression? {
    val arguments = initialExpression.arguments
    return if (
      arguments is TupleExpression &&
      (arguments.expressions.singleOrNull() is NamedArgumentListExpression)
    ) {
      initialExpression
    } else {
      null
    }
  }
}

/*
if (source.name.endsWith('ExperimentSpec.groovy')) {
            println(source.name)
            source.AST.classes.each { classNode ->
                println(classNode)
                println("**** FIELDS")
                classNode.fields.each { field ->
                    println(field.initialExpression)
                }
                println("**** METHODS")
                classNode.methods
//                    .findAll { it.code instanceof BlockStatement }
                    .each { method ->
                        println(method)
                        println(method.code)
//                        def existingStatements = ((BlockStatement) method.code).statements
//                        existingStatements.each {
//                            println('it')
//                            println(it)
//                        }
//                        existingStatements
//                            .findAll { it instanceof ExpressionStatement && it.expression instanceof MethodCallExpression }
//                            .each { existingStatement ->
//                                def methodCall = (existingStatement as ExpressionStatement).expression as MethodCallExpression
//                                println(methodCall)
//                                println(methodCall.arguments)
//                            }
                    }
            }
        }
 */
