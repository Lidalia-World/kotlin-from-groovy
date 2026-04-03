package uk.org.lidalia.kotlinfromgroovy

import spock.lang.PendingFeature
import spock.lang.Specification
import uk.org.lidalia.kotlinfromgroovy.testsupport.ClassWithDefaultProperties
import uk.org.lidalia.kotlinfromgroovy.testsupport.DataClassWithDefaultValues

class ConstructorArgumentsSpec extends Specification {

    def 'can construct an instance with default arguments'() {

        when:
            def instance = new ClassWithDefaultProperties()

        then:
            assert (instance.argument1 == "argument1")
            assert (instance.argument2 == "argument2")
    }

    @PendingFeature
    def 'can construct an instance with default arguments with one positional argument'() {

        when:
            def instance = new ClassWithDefaultProperties("different argument1")

        then:
            assert (instance.argument1 == "different argument1")
            assert (instance.argument2 == "argument2")
    }

    def 'can construct an instance with default arguments with two positional arguments'() {

        when:
            def instance = new ClassWithDefaultProperties("different argument1", "different argument2")

        then:
            assert (instance.argument1 == "different argument1")
            assert (instance.argument2 == "different argument2")
    }

    def 'can construct an instance with default arguments with first named argument'() {

        when:
            def instance = new ClassWithDefaultProperties(argument1: "different argument1")

        then:
            assert (instance.argument1 == "different argument1")
            assert (instance.argument2 == "argument2")
    }

    def 'can construct an instance with default arguments with second named argument'() {

        when:
            def instance = new ClassWithDefaultProperties(argument2: "different argument2")

        then:
            assert (instance.argument1 == "argument1")
            assert (instance.argument2 == "different argument2")
    }

    def 'can construct an instance with default arguments with both named arguments'() {

        when:
            def instance = new ClassWithDefaultProperties(
                argument1: "different argument1",
                argument2: "different argument2",
            )

        then:
            assert (instance.argument1 == "different argument1")
            assert (instance.argument2 == "different argument2")
    }

    def 'can construct an instance with default arguments with both named arguments in wrong order'() {

        when:
            def instance = new ClassWithDefaultProperties(
                argument2: "different argument2",
                argument1: "different argument1",
            )

        then:
            assert (instance.argument1 == "different argument1")
            assert (instance.argument2 == "different argument2")
    }

    @PendingFeature
    def 'can construct an instance with named argument and subsequent positional arguments'() {

        when:
            def instance = new ClassWithDefaultProperties(
                argument1: "different argument1",
                "different argument2",
            )

        then:
            assert (instance.argument1 == "different argument1")
            assert (instance.argument2 == "different argument2")
    }

    def 'can construct an instance with positional and subsequent positionally correct named argument'() {

        when:
            def instance = new ClassWithDefaultProperties(
                "different argument1",
                argument2: "different argument2",
            )

        then:
            assert (instance.argument1 == "different argument1")
            assert (instance.argument2 == "different argument2")
    }

    @PendingFeature
    def 'cannot construct an instance with positional and named arguments in wrong order'() {

        when:
            new ClassWithDefaultProperties(
                argument2: "different argument2",
                "different argument1",
            )


        then:
            def exception = thrown(IllegalArgumentException)
            exception.message == 'Mixing named and positioned arguments is not allowed'
    }

    def 'cannot construct an instance with positional and named arguments in wrong order 2'() {

        when:
            new ClassWithDefaultProperties(
                "different argument1",
                argument1: "different argument1",
            )


        then:
            def exception = thrown(IllegalArgumentException)
            exception.message == 'An argument is already passed for this parameter'
    }

    def 'cannot construct an instance with an incorrect argument'() {

        when:
            new ClassWithDefaultProperties(
                argument3: "does not exist",
            )

        then:
            def exception = thrown(IllegalArgumentException)
            exception.message == 'Cannot find a parameter with this name: argument3'
    }
}
