package uk.org.lidalia.kotlinfromgroovy

import groovy.lang.MissingMethodException
import spock.lang.Specification
import static uk.org.lidalia.kotlinfromgroovy.testsupport.ExtensionFunctionsKt.greetWith

class ExtensionFunctionExplicitImportSpec extends Specification {

    def 'explicitly imported extension function resolves'() {

        expect:
            'World'.greetWith('Hello') == 'Hello, World!'
    }

    def 'non-imported extension function from same class does not resolve'() {

        when:
            'World'.greetWithDefault()

        then:
            thrown(MissingMethodException)
    }
}
