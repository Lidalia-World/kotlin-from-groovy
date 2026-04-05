package uk.org.lidalia.kotlinfromgroovy

import groovy.lang.MissingMethodException
import spock.lang.Specification
import uk.org.lidalia.kotlinfromgroovy.testsupport.ExtensionFunctionsKt

class ExtensionFunctionClassImportSpec extends Specification {

    def 'importing the Kt facade class does not bring extension functions into scope'() {

        when:
            'World'.greetWith('Hello')

        then:
            thrown(MissingMethodException)
    }
}
