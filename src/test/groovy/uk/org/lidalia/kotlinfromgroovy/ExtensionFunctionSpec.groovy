package uk.org.lidalia.kotlinfromgroovy

import spock.lang.PendingFeature
import spock.lang.Specification

class ExtensionFunctionSpec extends Specification {

    @PendingFeature
    def 'can call an extension function'() {

        expect:
            'World'.greetWith('Hello') == 'Hello, World!'
    }

    @PendingFeature
    def 'can call an extension function with default argument'() {

        expect:
            'World'.greetWithDefault() == 'Hello, World!'
    }

    @PendingFeature
    def 'can call an extension function with explicit argument overriding default'() {

        expect:
            'World'.greetWithDefault('Hi') == 'Hi, World!'
    }

    @PendingFeature
    def 'can call an extension function with named arguments'() {

        expect:
            'World'.greetWith(greeting: 'Hey') == 'Hey, World!'
    }

    @PendingFeature
    def 'can call an extension function with named argument overriding default'() {

        expect:
            'content'.formatWith(prefix: '<<', suffix: '>>') == '<<content>>'
    }

    @PendingFeature
    def 'can call an extension function with partial named args using default for rest'() {

        expect:
            'content'.formatWith(prefix: '>>') == '>>content.'
    }
}
