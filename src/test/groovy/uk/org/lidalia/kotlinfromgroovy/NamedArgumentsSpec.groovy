package uk.org.lidalia.kotlinfromgroovy

import spock.lang.PendingFeature
import spock.lang.Specification
import uk.org.lidalia.kotlinfromgroovy.testsupport.Call
import uk.org.lidalia.kotlinfromgroovy.testsupport.ClassWithNoDefaultedArgumentsToMethods

class NamedArgumentsSpec extends Specification {

    def classUnderTest = new ClassWithNoDefaultedArgumentsToMethods()

    def 'can call a method using named arguments'() {

        when:
            classUnderTest.functionWithMultipleArguments(
                argument1: 'argument1',
                argument2: 2,
                argument3: true
            )

        then:
            classUnderTest.calls == [
                new Call(
                    'functionWithMultipleArguments',
                    [
                        argument1: 'argument1',
                        argument2: 2,
                        argument3: true
                    ]
                )
            ]
    }

    def 'can call a method that takes a Map'() {

        when:
            classUnderTest.functionWithMapArgument(
                entry1: 'entry1',
            )

        then:
            classUnderTest.calls == [
                new Call(
                    'functionWithMapArgument',
                    [
                        argument1: [entry1: 'entry1']
                    ]
                )
            ]
    }

    def 'can call a method that takes a Map using named arguments'() {

        when:
            classUnderTest.functionWithMapArgument(
                argument1: [entry1: 'entry1'],
            )

        then:
            classUnderTest.calls == [
                new Call(
                    'functionWithMapArgument',
                    [
                        argument1: [argument1: [entry1: 'entry1']]
                    ]
                )
            ]
    }

    def 'can call a method that takes a Map and another arg'() {

        when:
            classUnderTest.functionWithMapAndOtherArgument(
                [entry1: 'entry1'],
                'argument2'
            )

        then:
            classUnderTest.calls == [
                new Call(
                    'functionWithMapAndOtherArgument',
                    [
                        argument1: [entry1: 'entry1'],
                        argument2: 'argument2'
                    ]
                )
            ]
    }

    def 'can call a method that takes a Map and another arg groovy style'() {

        when:
            classUnderTest.functionWithMapAndOtherArgument(
                'argument1', entry1: 'entry1',
            )

        then:
            classUnderTest.calls == [
                new Call(
                    'functionWithMapAndOtherArgument',
                    [
                        argument1: [entry1: 'entry1'],
                        argument2: 'argument1',
                    ]
                )
            ]
    }

    def 'can call a method that takes a Map and another arg using named arguments'() {

        when:
            classUnderTest.functionWithMapAndOtherArgument(
                argument1: [entry1: 'entry1'],
                argument2: 'argument2',
            )

        then:
            classUnderTest.calls == [
                new Call(
                    'functionWithMapAndOtherArgument',
                    [
                        argument1: [entry1: 'entry1'],
                        argument2: 'argument2'
                    ]
                )
            ]
    }

    def 'can call a method that takes another arg and a Map'() {

        when:
            classUnderTest.functionWithOtherAndMapArgument(
                'argument1',
                [entry1: 'entry1'],
            )

        then:
            classUnderTest.calls == [
                new Call(
                    'functionWithOtherAndMapArgument',
                    [
                        argument1: 'argument1',
                        argument2: [entry1: 'entry1'],
                    ]
                )
            ]
    }

    def 'can call a method that takes another arg and a Map using named arguments'() {

        when:
            classUnderTest.functionWithOtherAndMapArgument(
                argument1: 'argument1',
                argument2: [entry1: 'entry1'],
            )

        then:
            classUnderTest.calls == [
                new Call(
                    'functionWithOtherAndMapArgument',
                    [
                        argument1: 'argument1',
                        argument2: [entry1: 'entry1'],
                    ]
                )
            ]
    }

    def 'can call a method that takes a LinkedHashMap'() {

        when:
            classUnderTest.functionWithLinkedMapArgument(
                entry1: 'entry1',
            )

        then:
            classUnderTest.calls == [
                new Call(
                    'functionWithLinkedMapArgument',
                    [
                        argument1: [entry1: 'entry1']
                    ]
                )
            ]
    }

    def 'cannot call a method with an incorrect argument'() {

        when:
            classUnderTest.functionWithMultipleArguments(argument4: "does not exist")

        then:
            def exception = thrown(IllegalArgumentException)
            exception.message == 'Cannot find a parameter with this name: argument4'
    }
}
