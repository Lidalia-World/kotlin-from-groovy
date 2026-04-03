package uk.org.lidalia.kotlinfromgroovy

import groovy.lang.MissingMethodException
import spock.lang.PendingFeature
import spock.lang.Specification

class GroovyInteropSmokeSpec extends Specification {

    def 'GString interpolation works'() {

        given:
            def name = 'World'

        expect:
            "Hello, ${name}!" == 'Hello, World!'
    }

    def 'closures work with collect'() {

        expect:
            [1, 2, 3].collect { it * 2 } == [2, 4, 6]
    }

    def 'closures work with findAll'() {

        expect:
            [1, 2, 3, 4].findAll { it % 2 == 0 } == [2, 4]
    }

    def 'closures work with inject'() {

        expect:
            [1, 2, 3].inject(0) { acc, val -> acc + val } == 6
    }

    def 'with scope function works'() {

        given:
            def list = []

        when:
            list.with {
                add('a')
                add('b')
            }

        then:
            list == ['a', 'b']
    }

    def 'tap returns the receiver'() {

        given:
            def list = [].tap {
                add('x')
            }

        expect:
            list == ['x']
    }

    def 'method call style works for size'() {

        given:
            def list = [1, 2, 3]

        expect:
            list.size() == 3
    }

    @PendingFeature
    def 'property style access on Java classes works'() {

        given:
            def list = [1, 2, 3]

        expect:
            list.size == 3
    }

    def 'property style access works'() {

        given:
            def str = 'hello'

        expect:
            str.class == String
    }

    def 'getAt on list works'() {

        given:
            def list = [1, 2, 3]

        expect:
            list[0] == 1
            list[1] == 2
    }

    def 'as type coercion works'() {

        expect:
            '42' as Integer == 42
    }

    def 'spread operator works'() {

        given:
            def words = ['hello', 'world']

        expect:
            words*.toUpperCase() == ['HELLO', 'WORLD']
    }

    def 'calling Java standard library methods works'() {

        expect:
            'hello'.toUpperCase() == 'HELLO'
            'Hello World'.contains('World')
            [3, 1, 2].sort() == [1, 2, 3]
    }

    def 'Expando dynamic properties work'() {

        given:
            def e = new Expando()
            e.name = 'test'
            e.greet = { "Hello, ${e.name}!" }

        expect:
            e.name == 'test'
            e.greet() == 'Hello, test!'
    }

    def 'map literal access works'() {

        given:
            def map = [a: 1, b: 2]

        expect:
            map.a == 1
            map['b'] == 2
    }

    def 'range works'() {

        expect:
            (1..5).collect() == [1, 2, 3, 4, 5]
    }

    def 'multiple assignment works with lists'() {

        given:
            def (a, b, c) = [1, 2, 3]

        expect:
            a == 1
            b == 2
            c == 3
    }

    def 'elvis operator works'() {

        expect:
            (null ?: 'default') == 'default'
            ('value' ?: 'default') == 'value'
    }

    def 'safe navigation operator works'() {

        given:
            String nullStr = null

        expect:
            nullStr?.toUpperCase() == null
            'hello'?.toUpperCase() == 'HELLO'
    }

    def 'closure coercion to interface works'() {

        given:
            Runnable r = { -> }

        when:
            r.run()

        then:
            noExceptionThrown()
    }

    def 'regex matching works'() {

        expect:
            'hello123' ==~ /[a-z]+\d+/
            !('hello' ==~ /\d+/)
    }

    def 'calling non-existent method on Java object throws MissingMethodException'() {

        when:
            'hello'.noSuchMethod()

        then:
            thrown(MissingMethodException)
    }

    def 'calling non-existent method on list throws MissingMethodException'() {

        when:
            [1, 2, 3].noSuchMethod()

        then:
            thrown(MissingMethodException)
    }

    def 'calling non-existent method on map throws MissingMethodException'() {

        when:
            [a: 1].noSuchMethod()

        then:
            thrown(MissingMethodException)
    }
}
