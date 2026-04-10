package uk.org.lidalia.kotlinfromgroovy

import spock.lang.Specification
import uk.org.lidalia.kotlinfromgroovy.testsupport.TypeConverter

import static uk.org.lidalia.kotlinfromgroovy.testsupport.InlineReifiedFunctionsKt.typeName
import static uk.org.lidalia.kotlinfromgroovy.testsupport.InlineReifiedFunctionsKt.isInstanceOf

class InlineReifiedSpec extends Specification {

    def 'can call inline reified top-level function with type argument'() {

        expect:
            typeName(String) == 'String'
    }

    def 'can call inline reified top-level function with different type'() {

        expect:
            typeName(Integer) == 'Integer'
    }

    def 'can call inline reified function that checks instance type'() {

        expect:
            isInstanceOf(String, 'hello') == true
            isInstanceOf(Integer, 'hello') == false
    }

    def 'can call inline reified member function with type argument'() {

        given:
            def converter = new TypeConverter()

        expect:
            converter.convert(Integer, '42') == 42
    }
}
