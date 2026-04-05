package uk.org.lidalia.kotlinfromgroovy

import spock.lang.Specification
import static uk.org.lidalia.kotlinfromgroovy.testsupport.ExtensionFunctionsKt.*

class ExtensionFunctionStaticStarImportSpec extends Specification {

    def 'static star import brings all extension functions into scope'() {

        expect:
            'World'.greetWith('Hello') == 'Hello, World!'
            'World'.greetWithDefault() == 'Hello, World!'
    }
}
