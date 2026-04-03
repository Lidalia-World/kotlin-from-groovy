package uk.org.lidalia.kotlinfromgroovy

import spock.lang.PendingFeature
import spock.lang.Specification
import uk.org.lidalia.kotlinfromgroovy.testsupport.Call
import uk.org.lidalia.kotlinfromgroovy.testsupport.ClassWithDefaultedArgumentsToMethods

class DefaultedArgumentsSpec extends Specification {

    private def classUnderTest = new ClassWithDefaultedArgumentsToMethods()

    @PendingFeature
    def 'can call functionWithOneDefaultedArgument with 0 args'() {

        when:
            classUnderTest.functionWithOneDefaultedArgument()

        then:
            classUnderTest.calls == [
                new Call(
                    'functionWithOneDefaultedArgument',
                    [
                        'argument1': 'argument1',
                    ]
                )
            ]
    }

    def 'can call functionWithOneDefaultedArgument with 1st positional arg'() {

        when:
            classUnderTest.functionWithOneDefaultedArgument('different')

        then:
            classUnderTest.calls == [
                new Call(
                    'functionWithOneDefaultedArgument',
                    [
                        'argument1': 'different',
                    ]
                )
            ]
    }

    def 'can call functionWithOneDefaultedArgument with 1st named arg'() {

        when:
            classUnderTest.functionWithOneDefaultedArgument(argument1: 'different')

        then:
            classUnderTest.calls == [
                new Call(
                    'functionWithOneDefaultedArgument',
                    [
                        'argument1': 'different',
                    ]
                )
            ]
    }

    def 'cannot call functionWithTwoArgumentFirstDefaulted with no args'() {

        when:
            classUnderTest.functionWithTwoArgumentFirstDefaulted()

        then:
            def exception = thrown(IllegalArgumentException)
            exception.message == 'No value passed for parameter \'argument2\''
    }

    @PendingFeature
    def 'cannot call functionWithTwoArgumentFirstDefaulted with 1 positional arg'() {

        when:
            classUnderTest.functionWithTwoArgumentFirstDefaulted('different 1')

        then:
            def exception = thrown(IllegalArgumentException)
            exception.message == 'No value passed for parameter \'argument2\''
    }

    def 'can call functionWithTwoArgumentFirstDefaulted with 2nd named arg'() {

        when:
            classUnderTest.functionWithTwoArgumentFirstDefaulted(argument2: 'different 2')

        then:
            classUnderTest.calls == [
                new Call(
                    'functionWithTwoArgumentFirstDefaulted',
                    [
                        'argument1': 'argument1',
                        'argument2': 'different 2',
                    ]
                )
            ]
    }

    def 'can call functionWithTwoArgumentFirstDefaulted with 2 positional args'() {

        when:
            classUnderTest.functionWithTwoArgumentFirstDefaulted('different 1', 'different 2')

        then:
            classUnderTest.calls == [
                new Call(
                    'functionWithTwoArgumentFirstDefaulted',
                    [
                        'argument1': 'different 1',
                        'argument2': 'different 2',
                    ]
                )
            ]
    }

    def 'can call functionWithTwoArgumentFirstDefaulted with 2 named args'() {

        when:
            classUnderTest.functionWithTwoArgumentFirstDefaulted(argument1: 'different 1', argument2: 'different 2')

        then:
            classUnderTest.calls == [
                new Call(
                    'functionWithTwoArgumentFirstDefaulted',
                    [
                        'argument1': 'different 1',
                        'argument2': 'different 2',
                    ]
                )
            ]
    }

    def 'can call functionWithTwoArgumentFirstDefaulted with 1 positional 1 named args'() {

        when:
            classUnderTest.functionWithTwoArgumentFirstDefaulted('different 1', argument2: 'different 2')

        then:
            classUnderTest.calls == [
                new Call(
                    'functionWithTwoArgumentFirstDefaulted',
                    [
                        'argument1': 'different 1',
                        'argument2': 'different 2',
                    ]
                )
            ]
    }

    @PendingFeature
    def 'cannot call functionWithTwoArgumentFirstDefaulted with mixed positional and named args'() {
        when:
            classUnderTest.functionWithTwoArgumentFirstDefaulted('different 1', argument1: 'different 2')

        then:
            def exception = thrown(IllegalArgumentException)
            exception.message == 'An argument is already passed for this parameter'

        when:
            classUnderTest.functionWithTwoArgumentFirstDefaulted(argument2: 'different 1', 'different 2')

        then:
            exception = thrown(IllegalArgumentException)
            exception.message == 'Mixing named and positioned arguments is not allowed'
    }

    @PendingFeature
    def 'can call functionWithTwoArgumentFirstDefaulted with 1 named 1 positional args'() {

        when:
            classUnderTest.functionWithTwoArgumentFirstDefaulted(argument1: 'different 2', 'different 1')

        then:
            classUnderTest.calls == [
                new Call(
                    'functionWithTwoArgumentFirstDefaulted',
                    [
                        'argument1': 'different 1',
                        'argument2': 'different 2',
                    ]
                )
            ]
    }

    @PendingFeature
    def 'cannot call functionWithTwoArgumentsSecondDefaulted with no args'() {

        when:
            classUnderTest.functionWithTwoArgumentsSecondDefaulted()

        then:
            def exception = thrown(IllegalArgumentException)
            exception.message == 'No value passed for parameter \'argument1\''
    }

    @PendingFeature
    def 'can call functionWithTwoArgumentsSecondDefaulted with 1 positional arg'() {

        when:
            classUnderTest.functionWithTwoArgumentsSecondDefaulted('different 1')

        then:
            classUnderTest.calls == [
                new Call(
                    'functionWithTwoArgumentsSecondDefaulted',
                    [
                        'argument1': 'different 1',
                        'argument2': 'argument2',
                    ]
                )
            ]
    }

    def 'cannot call functionWithTwoArgumentsSecondDefaulted with 2nd named arg'() {

        when:
            classUnderTest.functionWithTwoArgumentsSecondDefaulted(argument2: 'different 2')

        then:
            def exception = thrown(IllegalArgumentException)
            exception.message == 'No value passed for parameter \'argument1\''
    }

    def 'can call functionWithTwoArgumentsSecondDefaulted with 2 positional args'() {

        when:
            classUnderTest.functionWithTwoArgumentsSecondDefaulted('different 1', 'different 2')

        then:
            classUnderTest.calls == [
                new Call(
                    'functionWithTwoArgumentsSecondDefaulted',
                    [
                        'argument1': 'different 1',
                        'argument2': 'different 2',
                    ]
                )
            ]
    }

    def 'can call functionWithTwoArgumentsSecondDefaulted with 2 named args'() {

        when:
            classUnderTest.functionWithTwoArgumentsSecondDefaulted(argument1: 'different 1', argument2: 'different 2')

        then:
            classUnderTest.calls == [
                new Call(
                    'functionWithTwoArgumentsSecondDefaulted',
                    [
                        'argument1': 'different 1',
                        'argument2': 'different 2',
                    ]
                )
            ]
    }

    @PendingFeature
    def 'can call functionWithTwoArgumentsBothDefaulted with no args'() {

        when:
            classUnderTest.functionWithTwoArgumentsBothDefaulted()

        then:
            classUnderTest.calls == [
                new Call(
                    'functionWithTwoArgumentsBothDefaulted',
                    [
                        'argument1': 'argument1',
                        'argument2': 'argument2',
                    ]
                )
            ]
    }

    @PendingFeature
    def 'can call functionWithTwoArgumentsBothDefaulted with 1 positional arg'() {

        when:
            classUnderTest.functionWithTwoArgumentsBothDefaulted('different 1')

        then:
            classUnderTest.calls == [
                new Call(
                    'functionWithTwoArgumentsBothDefaulted',
                    [
                        'argument1': 'different 1',
                        'argument2': 'argument2',
                    ]
                )
            ]
    }

    def 'can call functionWithTwoArgumentsBothDefaulted with 2nd named arg'() {

        when:
            classUnderTest.functionWithTwoArgumentsBothDefaulted(argument2: 'different 2')

        then:
            classUnderTest.calls == [
                new Call(
                    'functionWithTwoArgumentsBothDefaulted',
                    [
                        'argument1': 'argument1',
                        'argument2': 'different 2',
                    ]
                )
            ]
    }

    def 'can call functionWithTwoArgumentsBothDefaulted with 2 positional args'() {

        when:
            classUnderTest.functionWithTwoArgumentsBothDefaulted('different 1', 'different 2')

        then:
            classUnderTest.calls == [
                new Call(
                    'functionWithTwoArgumentsBothDefaulted',
                    [
                        'argument1': 'different 1',
                        'argument2': 'different 2',
                    ]
                )
            ]
    }

    def 'can call functionWithTwoArgumentsBothDefaulted with 2 named args'() {

        when:
            classUnderTest.functionWithTwoArgumentsBothDefaulted(argument1: 'different 1', argument2: 'different 2')

        then:
            classUnderTest.calls == [
                new Call(
                    'functionWithTwoArgumentsBothDefaulted',
                    [
                        'argument1': 'different 1',
                        'argument2': 'different 2',
                    ]
                )
            ]
    }

    @PendingFeature
    def 'can call functionWithOneNullableArgumentDefaultedToNotNull with no args'() {
        when:
            classUnderTest.functionWithOneNullableArgumentDefaultedToNotNull()

        then:
            classUnderTest.calls == [
                new Call(
                    'functionWithOneNullableArgumentDefaultedToNotNull',
                    [
                        'argument1': 'argument1',
                    ]
                )
            ]
    }

    def 'can call functionWithOneNullableArgumentDefaultedToNotNull with one positional null arg'() {
        when:
            classUnderTest.functionWithOneNullableArgumentDefaultedToNotNull(null)

        then:
            classUnderTest.calls == [
                new Call(
                    'functionWithOneNullableArgumentDefaultedToNotNull',
                    [
                        'argument1': null,
                    ]
                )
            ]
    }

    def 'can call functionWithOneNullableArgumentDefaultedToNotNull with one positional not null arg'() {
        when:
            classUnderTest.functionWithOneNullableArgumentDefaultedToNotNull('different')

        then:
            classUnderTest.calls == [
                new Call(
                    'functionWithOneNullableArgumentDefaultedToNotNull',
                    [
                        'argument1': 'different',
                    ]
                )
            ]
    }

    def 'can call functionWithOneNullableArgumentDefaultedToNotNull with one named null arg'() {
        when:
            classUnderTest.functionWithOneNullableArgumentDefaultedToNotNull(argument1: null)

        then:
            classUnderTest.calls == [
                new Call(
                    'functionWithOneNullableArgumentDefaultedToNotNull',
                    [
                        'argument1': null,
                    ]
                )
            ]
    }

    def 'can call functionWithOneNullableArgumentDefaultedToNotNull with one named not null arg'() {
        when:
            classUnderTest.functionWithOneNullableArgumentDefaultedToNotNull(argument1: 'different')

        then:
            classUnderTest.calls == [
                new Call(
                    'functionWithOneNullableArgumentDefaultedToNotNull',
                    [
                        'argument1': 'different',
                    ]
                )
            ]
    }

    def 'can call functionWithOneNullableArgumentDefaultedToNull with no args'() {
        when:
            classUnderTest.functionWithOneNullableArgumentDefaultedToNull()

        then:
            classUnderTest.calls == [
                new Call(
                    'functionWithOneNullableArgumentDefaultedToNull',
                    [
                        'argument1': null,
                    ]
                )
            ]
    }

    def 'can call functionWithOneNullableArgumentDefaultedToNull with one positional null arg'() {
        when:
            classUnderTest.functionWithOneNullableArgumentDefaultedToNull(null)

        then:
            classUnderTest.calls == [
                new Call(
                    'functionWithOneNullableArgumentDefaultedToNull',
                    [
                        'argument1': null,
                    ]
                )
            ]
    }

    def 'can call functionWithOneNullableArgumentDefaultedToNull with one positional not null arg'() {
        when:
            classUnderTest.functionWithOneNullableArgumentDefaultedToNull('different')

        then:
            classUnderTest.calls == [
                new Call(
                    'functionWithOneNullableArgumentDefaultedToNull',
                    [
                        'argument1': 'different',
                    ]
                )
            ]
    }

    def 'can call functionWithOneNullableArgumentDefaultedToNull with one named null arg'() {
        when:
            classUnderTest.functionWithOneNullableArgumentDefaultedToNull(argument1: null)

        then:
            classUnderTest.calls == [
                new Call(
                    'functionWithOneNullableArgumentDefaultedToNull',
                    [
                        'argument1': null,
                    ]
                )
            ]
    }

    def 'can call functionWithOneNullableArgumentDefaultedToNull with one named not null arg'() {
        when:
            classUnderTest.functionWithOneNullableArgumentDefaultedToNull(argument1: 'different')

        then:
            classUnderTest.calls == [
                new Call(
                    'functionWithOneNullableArgumentDefaultedToNull',
                    [
                        'argument1': 'different',
                    ]
                )
            ]
    }
}
