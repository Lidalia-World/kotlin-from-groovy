package uk.org.lidalia.kotlinfromgroovy

import org.codehaus.groovy.runtime.wrappers.PojoWrapper
import spock.lang.Specification
import uk.org.lidalia.kotlinfromgroovy.testsupport.ClassThatThrowsInConstructor
import uk.org.lidalia.kotlinfromgroovy.testsupport.ClassWithDefaultedArgumentsToMethods
import uk.org.lidalia.kotlinfromgroovy.testsupport.ClassWithNoDefaultedArgumentsToMethods
import uk.org.lidalia.kotlinfromgroovy.testsupport.JavaClassWithoutPrimaryConstructor
import uk.org.lidalia.kotlinfromgroovy.testsupport.GroovySubclass
import uk.org.lidalia.kotlinfromgroovy.testsupport.OpenClassWithDefaults

class ConsumingProjectCompatibilitySpec extends Specification {

    // Issue 1: Java classes without a Kotlin primary constructor
    // should be constructable via Groovy when named args are used.
    // Named args bypass early Groovy dispatch and reach Kotlin reflection,
    // which must fall back to Groovy for non-Kotlin classes.

    def 'can construct a Java class with named args'() {

        when:
            def instance = new JavaClassWithoutPrimaryConstructor(value: 'custom')

        then:
            notThrown(Exception)
            instance.value == 'custom'
    }

    // Issue 2: Exceptions thrown inside a Kotlin constructor should
    // propagate as their original type, not wrapped in InvocationTargetException.
    // KFunction.callBy() wraps exceptions when default params are involved.

    def 'exception in Kotlin constructor propagates unwrapped'() {

        when:
            new ClassThatThrowsInConstructor()

        then:
            def exception = thrown(IllegalStateException)
            exception.message == 'Constructor failed: value not provided'
    }

    // Issue 3: Groovy PojoWrapper values should be unwrapped before
    // type checking against Kotlin parameter types.
    // Groovy's metaclass layer can wrap values in PojoWrapper during dispatch.

    def 'can call Kotlin method with PojoWrapper-wrapped argument'() {

        given:
            def classUnderTest = new ClassWithNoDefaultedArgumentsToMethods()
            def wrapped = new PojoWrapper('hello', String)

        when:
            classUnderTest.functionWithMultipleArguments(wrapped, 2, true)

        then:
            notThrown(Exception)
            classUnderTest.calls[0].arguments == [
                argument1: 'hello',
                argument2: 2,
                argument3: true,
            ]
    }

    // Issue 4: Groovy can pass null for the args parameter of
    // invokeMethod; this should not cause a NullPointerException
    // inside KotlinAwareMetaClass. When null is passed, it should be
    // treated as "no arguments" so default parameter values are used.

    // Issue 5: Groovy classes that extend a Kotlin class and call super()
    // should work correctly. The AST transform must not rewrite super()
    // or this() constructor delegation calls into constructWithNamedArgs,
    // because those calls initialise the current object's parent — they
    // do not create a new instance.

    def 'Groovy subclass can call super() on a Kotlin open class'() {

        when:
            def instance = new GroovySubclass('custom')

        then:
            notThrown(Exception)
            instance.value == 'custom'
    }

    def 'Groovy subclass can call super() with defaults on a Kotlin open class'() {

        when:
            def instance = new GroovySubclass()

        then:
            notThrown(Exception)
            instance.value == 'default'
    }

    // Issue 6: Groovy can pass null for the args Array parameter of
    // invokeMethod; this should not cause a NullPointerException
    // inside KotlinAwareMetaClass. When null is passed as Object[],
    // it should be treated as "no arguments" so default parameter
    // values are used.

    def 'invokeMethod handles null args array for method with defaults'() {

        given:
            def instance = new ClassWithDefaultedArgumentsToMethods()

        when:
            instance.metaClass.invokeMethod(instance, 'functionWithOneDefaultedArgument', (Object[]) null)

        then:
            notThrown(Exception)
            instance.calls[0].arguments == [argument1: 'argument1']
    }

    // Issue 7: Groovy can pass null for the args parameter of
    def 'invokeMethod handles null args for method with defaults'() {

        given:
            def instance = new ClassWithDefaultedArgumentsToMethods()

        when:
            instance.metaClass.invokeMethod(instance, 'functionWithOneDefaultedArgument', (Object) null)

        then:
            notThrown(Exception)
            instance.calls[0].arguments == [argument1: 'argument1']
    }

}
