package uk.org.lidalia.kotlinfromgroovy

import groovy.lang.MissingMethodException
import spock.lang.Specification
import uk.org.lidalia.kotlinfromgroovy.testsupport.*

class ExtensionFunctionPackageStarImportSpec extends Specification {

    def 'package star import does not bring extension functions into scope'() {

        when:
            'World'.greetWith('Hello')

        then:
            thrown(MissingMethodException)
    }
}
