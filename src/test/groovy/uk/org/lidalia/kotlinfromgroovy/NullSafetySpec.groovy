package uk.org.lidalia.kotlinfromgroovy

import spock.lang.Specification
import uk.org.lidalia.kotlinfromgroovy.testsupport.ClassWithDefaultedArgumentsToMethods
import uk.org.lidalia.kotlinfromgroovy.testsupport.ClassWithNoDefaultedArgumentsToMethods
import uk.org.lidalia.kotlinfromgroovy.testsupport.DataClass

class NullSafetySpec extends Specification {

    def 'passing null to non-nullable parameter throws IllegalArgumentException'() {

        given:
            def classUnderTest = new ClassWithNoDefaultedArgumentsToMethods()
            IllegalArgumentException caught = null

        when:
            try {
                classUnderTest.functionWithMultipleArguments(null, 2, true)
            } catch (IllegalArgumentException e) {
                caught = e
            }

        then:
            caught != null
            caught.message.toLowerCase().contains('null')
    }

    def 'passing null to non-nullable named parameter throws IllegalArgumentException'() {

        given:
            def classUnderTest = new ClassWithNoDefaultedArgumentsToMethods()
            IllegalArgumentException caught = null

        when:
            try {
                classUnderTest.functionWithMultipleArguments(
                    argument1: null,
                    argument2: 2,
                    argument3: true,
                )
            } catch (IllegalArgumentException e) {
                caught = e
            }

        then:
            caught != null
            caught.message.toLowerCase().contains('null')
    }

    def 'passing null to nullable parameter works'() {

        given:
            def classUnderTest = new ClassWithDefaultedArgumentsToMethods()

        when:
            classUnderTest.functionWithOneNullableArgumentDefaultedToNotNull(null)

        then:
            classUnderTest.calls.size() == 1
            classUnderTest.calls[0].arguments == [argument1: null]
    }

    def 'passing null to non-nullable constructor parameter throws IllegalArgumentException'() {

        given:
            IllegalArgumentException caught = null

        when:
            try {
                new DataClass(null, 2, true)
            } catch (IllegalArgumentException e) {
                caught = e
            }

        then:
            caught != null
            caught.message.toLowerCase().contains('null')
    }
}
