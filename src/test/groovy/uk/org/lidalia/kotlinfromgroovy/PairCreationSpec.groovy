package uk.org.lidalia.kotlinfromgroovy

import kotlin.Pair
import spock.lang.Specification

class PairCreationSpec extends Specification {

    def 'can create a Pair using to'() {

        when:
            def result = 'key'.to('value')

        then:
            result == new Pair('key', 'value')
    }

    def 'can destructure a Pair created with to'() {

        when:
            def (key, value) = 'key'.to('value')

        then:
            key == 'key'
            value == 'value'
    }
}
