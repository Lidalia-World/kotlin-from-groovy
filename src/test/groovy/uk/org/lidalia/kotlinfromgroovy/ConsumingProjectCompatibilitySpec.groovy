package uk.org.lidalia.kotlinfromgroovy

import org.codehaus.groovy.runtime.wrappers.PojoWrapper
import spock.lang.Specification
import uk.org.lidalia.kotlinfromgroovy.testsupport.ClassThatThrowsInConstructor
import uk.org.lidalia.kotlinfromgroovy.testsupport.ClassWithDefaultedArgumentsToMethods
import uk.org.lidalia.kotlinfromgroovy.testsupport.ClassWithNoDefaultedArgumentsToMethods
import uk.org.lidalia.kotlinfromgroovy.testsupport.JavaClassWithoutPrimaryConstructor
import uk.org.lidalia.kotlinfromgroovy.testsupport.GroovySubclass
import uk.org.lidalia.kotlinfromgroovy.testsupport.OpenClassWithDefaults
import uk.org.lidalia.kotlinfromgroovy.testsupport.SimpleCallback
import uk.org.lidalia.kotlinfromgroovy.testsupport.GroovySubclassOfBaseWithPrivateMethod

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

    // Issue 4: PojoWrapper wrapping null should be treated as null,
    // not as a PojoWrapper value. Groovy can wrap null in a PojoWrapper
    // when casting (e.g. (String) null), and the type check must
    // unwrap it to null rather than failing with a type mismatch.

    def 'can call Kotlin method with PojoWrapper wrapping null on nullable param'() {

        given:
            def classUnderTest = new ClassWithDefaultedArgumentsToMethods()
            def wrapped = new PojoWrapper(null, String)

        when:
            classUnderTest.functionWithOneNullableArgumentDefaultedToNotNull(wrapped)

        then:
            notThrown(Exception)
            classUnderTest.calls[0].arguments == [argument1: null]
    }

    // Issue 5: Groovy can pass null for the args parameter of
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

    // Issue 6: Anonymous inner classes implementing a Kotlin interface
    // should work. The AST transform must not rewrite anonymous class
    // constructors into constructWithNamedArgs, because anonymous inner
    // classes have hidden constructor parameters (enclosing instance).

    def 'can create anonymous inner class implementing a Kotlin interface'() {

        when:
            def callback = new SimpleCallback() {
                @Override
                String execute() {
                    return 'hello from anonymous'
                }
            }

        then:
            notThrown(Exception)
            callback.execute() == 'hello from anonymous'
    }

    // Issue 7: Groovy can pass null for the args Array parameter of
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

    // Issue 8: Groovy can pass null for the args parameter of
    def 'invokeMethod handles null args for method with defaults'() {

        given:
            def instance = new ClassWithDefaultedArgumentsToMethods()

        when:
            instance.metaClass.invokeMethod(instance, 'functionWithOneDefaultedArgument', (Object) null)

        then:
            notThrown(Exception)
            instance.calls[0].arguments == [argument1: 'argument1']
    }

    // Issue 9: Map literals passed as arguments should be treated as
    // positional values, not as named arguments. The AST transform
    // must not confuse a MapExpression (e.g. [:] or [key: value]) with
    // NamedArgumentListExpression (e.g. foo(key: value)).

    def 'can pass empty map literal to a method that takes a Map parameter'() {

        given:
            def classUnderTest = new ClassWithNoDefaultedArgumentsToMethods()

        when:
            classUnderTest.functionWithMapArgument([:])

        then:
            notThrown(Exception)
            classUnderTest.calls[0].arguments == [argument1: [:]]
    }

    def 'can pass non-empty map literal to a method that takes a Map parameter'() {

        given:
            def classUnderTest = new ClassWithNoDefaultedArgumentsToMethods()

        when:
            classUnderTest.functionWithMapArgument([key1: 'value1', key2: 'value2'])

        then:
            notThrown(Exception)
            classUnderTest.calls[0].arguments == [argument1: [key1: 'value1', key2: 'value2']]
    }

    // Issue 10: Groovy GStrings (interpolated strings like "$var")
    // should be coerced to String when passed to Kotlin methods
    // expecting String parameters.

    def 'GString is coerced to String when passed to Kotlin method with defaults'() {

        given:
            def classUnderTest = new ClassWithDefaultedArgumentsToMethods()
            def name = 'world'

        when:
            classUnderTest.functionWithTwoArgumentsSecondDefaulted("hello $name")

        then:
            notThrown(Exception)
            classUnderTest.calls[0].arguments == [
                argument1: 'hello world',
                argument2: 'argument2',
            ]
    }

    // Issue 11: Groovy callers can omit trailing nullable parameters
    // that have no default value. Groovy normally fills these with null.
    // The Kotlin-aware dispatch must not reject such calls.

    def 'can omit trailing nullable param without default when calling Kotlin method'() {

        given:
            def classUnderTest = new ClassWithDefaultedArgumentsToMethods()

        when:
            classUnderTest.functionWithDefaultsAndTrailingNullable('a', 'b')

        then:
            notThrown(Exception)
            classUnderTest.calls[0].arguments == [
                argument1: 'a',
                argument2: 'b',
                argument3: null,
            ]
    }

    // Issue 12: Groovy callers may pass null for a non-null Kotlin
    // parameter that has a default value. This is a Groovy convention
    // meaning "use the default." The interop layer should use the
    // Kotlin default instead of throwing NullPointerException.

    def 'passing null to non-null param with default uses the default value'() {

        given:
            def classUnderTest = new ClassWithDefaultedArgumentsToMethods()

        when:
            classUnderTest.functionWithTwoArgumentsBothDefaulted('explicit', null)

        then:
            notThrown(NullPointerException)
            classUnderTest.calls[0].arguments == [
                argument1: 'explicit',
                argument2: 'argument2',
            ]
    }

    // Issue 13: Private methods in Groovy traits must remain callable
    // from within the trait. The KotlinAwareMetaClass global handler
    // must not interfere with Groovy's internal trait dispatch.

    def 'can call private method in superclass from subclass instance'() {

        given:
            def instance = new GroovySubclassOfBaseWithPrivateMethod()

        when:
            def result = instance.callPrivateMethod('hello')

        then:
            notThrown(Exception)
            result == 'processed: hello'
    }

}
