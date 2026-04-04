package uk.org.lidalia.kotlinfromgroovy

import spock.lang.Specification
import uk.org.lidalia.kotlinfromgroovy.testsupport.ClassWithDefaultedArgumentsToMethods
import uk.org.lidalia.kotlinfromgroovy.testsupport.ClassWithNoDefaultedArgumentsToMethods
import uk.org.lidalia.kotlinfromgroovy.testsupport.DataClass

class NullSafetySpec extends Specification {

    def 'passing null to non-nullable parameter throws NullPointerException'() {

        given:
            def classUnderTest = new ClassWithNoDefaultedArgumentsToMethods()

        when:
            classUnderTest.functionWithMultipleArguments(null, 2, true)

        then:
            def exception = thrown(NullPointerException)
            exception.message.toLowerCase().contains('null')
    }

    def 'passing null to non-nullable named parameter throws NullPointerException'() {

        given:
            def classUnderTest = new ClassWithNoDefaultedArgumentsToMethods()

        when:
            classUnderTest.functionWithMultipleArguments(
                argument1: null,
                argument2: 2,
                argument3: true,
            )

        then:
            def exception = thrown(NullPointerException)
            exception.message.toLowerCase().contains('null')
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

    def 'passing null to non-nullable constructor parameter throws NullPointerException'() {

        when:
            new DataClass(null, 2, true)

        then:
            def exception = thrown(NullPointerException)
            exception.message.toLowerCase().contains('null')
    }
}
