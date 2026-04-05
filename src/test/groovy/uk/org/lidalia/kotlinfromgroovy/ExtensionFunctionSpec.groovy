package uk.org.lidalia.kotlinfromgroovy

import groovy.lang.MissingMethodException
import spock.lang.Specification
import uk.org.lidalia.kotlinfromgroovy.testsupport.DataClass
import static uk.org.lidalia.kotlinfromgroovy.testsupport.ExtensionFunctionsKt.*

class ExtensionFunctionSpec extends Specification {

    def 'can call an extension function'() {

        expect:
            'World'.greetWith('Hello') == 'Hello, World!'
    }

    def 'can call an extension function with default argument'() {

        expect:
            'World'.greetWithDefault() == 'Hello, World!'
    }

    def 'can call an extension function with explicit argument overriding default'() {

        expect:
            'World'.greetWithDefault('Hi') == 'Hi, World!'
    }

    def 'can call an extension function with named arguments'() {

        expect:
            'World'.greetWith(greeting: 'Hey') == 'Hey, World!'
    }

    def 'can call an extension function with named argument overriding default'() {

        expect:
            'content'.formatWith(prefix: '<<', suffix: '>>') == '<<content>>'
    }

    def 'can call an extension function with partial named args using default for rest'() {

        expect:
            'content'.formatWith(prefix: '>>') == '>>content.'
    }

    def 'can call an extension function on a data class'() {

        given:
            def dc = new DataClass('hello', 42, true)

        expect:
            dc.describe() == 'DataClass(hello, 42, true)'
    }

    def 'can call an extension function on a data class with arguments'() {

        given:
            def dc = new DataClass('hello', 42, true)

        expect:
            dc.withLabel('Value') == 'Value: hello'
    }

    def 'can call an extension function on a data class with named arguments'() {

        given:
            def dc = new DataClass('hello', 42, true)

        expect:
            dc.withLabel(label: 'X', separator: ' -> ') == 'X -> hello'
    }

    def 'can call an extension function on a data class with default argument'() {

        given:
            def dc = new DataClass('hello', 42, true)

        expect:
            dc.withLabel('Tag') == 'Tag: hello'
    }

    def 'can call an extension function on a List via interface'() {

        expect:
            [10, 20, 30].secondOrNull() == 20
    }

    def 'can call an extension function on an empty list via interface'() {

        expect:
            [].secondOrNull() == null
    }

    def 'can call an extension function on a Collection supertype with a List'() {

        expect:
            [1, 2, 3].describeSize() == '3 items'
    }

    def 'can call an extension function on a Collection supertype with a Set'() {

        when:
            def result = ([1, 2] as Set).describeSize()

        then:
            result == '2 items'
    }

    def 'can call an extension function on a Collection with named argument'() {

        expect:
            [1, 2].describeSize(label: 'elements') == '2 elements'
    }

    def 'can call an extension function with nullable argument'() {

        expect:
            'hello'.wrapWith('<<', '>>') == '<<hello>>'
    }

    def 'can call an extension function with null argument'() {

        expect:
            'hello'.wrapWith(null) == 'hello'
    }

    def 'can call an extension function on a Map'() {

        expect:
            [a: 1, b: 2].describeEntries() == 'a=1, b=2'
    }

    def 'can call an extension function from the same package without import'() {

        expect:
            'World'.samePackageGreet() == 'same-package: World'
    }

    def 'unimported extension function from different package does not resolve'() {

        when:
            'hello'.unimportedGreet()

        then:
            thrown(MissingMethodException)
    }

    def 'null dereferencing works'() {

        given:
            String nullReference = null

        expect:
            nullReference?.wrapWith('<<', '>>') == null
    }
}
