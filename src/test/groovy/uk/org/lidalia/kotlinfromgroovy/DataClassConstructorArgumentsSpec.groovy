package uk.org.lidalia.kotlinfromgroovy

import spock.lang.PendingFeature
import spock.lang.Specification
import uk.org.lidalia.kotlinfromgroovy.testsupport.DataClassWithDefaultValues

class DataClassConstructorArgumentsSpec extends Specification {

    def 'can construct a data class with default arguments'() {

        when:
            def instance = new DataClassWithDefaultValues()

        then:
            assert (instance.argument1 == "argument1")
            assert (instance.argument2 == "argument2")
    }

    @PendingFeature
    def 'can construct a data class with default arguments with one positional argument'() {

        when:
            def instance = new DataClassWithDefaultValues("different argument1")

        then:
            assert (instance.argument1 == "different argument1")
            assert (instance.argument2 == "argument2")
    }

    def 'can construct a data class with default arguments with two positional arguments'() {

        when:
            def instance = new DataClassWithDefaultValues("different argument1", "different argument2")

        then:
            assert (instance.argument1 == "different argument1")
            assert (instance.argument2 == "different argument2")
    }

    def 'can construct a data class with default arguments with first named argument'() {

        when:
            def instance = new DataClassWithDefaultValues(argument1: "different argument1")

        then:
            assert (instance.argument1 == "different argument1")
            assert (instance.argument2 == "argument2")
    }

    @PendingFeature
    def 'can construct a data class with default arguments with second named argument'() {

        when:
            def instance = new DataClassWithDefaultValues(argument2: "different argument2")

        then:
            assert (instance.argument1 == "argument1")
            assert (instance.argument2 == "different argument2")
    }

    @PendingFeature
    def 'can construct a data class with default arguments with both named arguments'() {

        when:
            def instance = new DataClassWithDefaultValues(
                argument1: "different argument1",
                argument2: "different argument2",
            )

        then:
            assert (instance.argument1 == "different argument1")
            assert (instance.argument2 == "different argument2")
    }

    @PendingFeature
    def 'can construct a data class with default arguments with both named arguments in wrong order'() {

        when:
            def instance = new DataClassWithDefaultValues(
                argument2: "different argument2",
                argument1: "different argument1",
            )

        then:
            assert (instance.argument1 == "different argument1")
            assert (instance.argument2 == "different argument2")
    }

    @PendingFeature
    def 'can construct a data class with named argument and subsequent positional arguments'() {

        when:
            def instance = new DataClassWithDefaultValues(
                argument1: "different argument1",
                "different argument2",
            )

        then:
            assert (instance.argument1 == "different argument1")
            assert (instance.argument2 == "different argument2")
    }

    @PendingFeature
    def 'can construct a data class with positional and subsequent positionally correct named argument'() {

        when:
            def instance = new DataClassWithDefaultValues(
                "different argument1",
                argument2: "different argument2",
            )

        then:
            assert (instance.argument1 == "different argument1")
            assert (instance.argument2 == "different argument2")
    }

    @PendingFeature
    def 'cannot construct a data class with positional and named arguments in wrong order'() {

        when:
            new DataClassWithDefaultValues(
                argument2: "different argument2",
                "different argument1",
            )

        then:
            def exception = thrown(IllegalArgumentException)
            exception.message == 'Mixing named and positioned arguments is not allowed'
    }

    @PendingFeature
    def 'cannot construct a data class with positional and named arguments in wrong order 2'() {

        when:
            new DataClassWithDefaultValues(
                "different argument1",
                argument1: "different argument1",
            )

        then:
            def exception = thrown(IllegalArgumentException)
            exception.message == 'An argument is already passed for this parameter'
    }

    @PendingFeature
    def 'cannot construct a data class with an incorrect argument'() {

        when:
            new DataClassWithDefaultValues(
                argument3: "does not exist",
            )

        then:
            def exception = thrown(IllegalArgumentException)
            exception.message == 'Cannot find a parameter with this name: argument3'
    }
}
