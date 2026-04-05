package uk.org.lidalia.kotlinfromgroovy

import spock.lang.PendingFeature
import spock.lang.Specification
import uk.org.lidalia.kotlinfromgroovy.testsupport.Counter
import static uk.org.lidalia.kotlinfromgroovy.testsupport.LimitationExamplesKt.concatWith

class KnownLimitationsSpec extends Specification {

    @PendingFeature
    def 'varargs are not supported'() {

        expect:
            KotlinInterop.callMethodWithNamedArgs(
                null,
                'joinAll',
                [:] as LinkedHashMap,
                ['a', 'b', 'c'] as Object[],
                false,
            ) == 'a, b, c'
    }

    def 'operator plus works via method name'() {

        given:
            def a = new Counter(3)
            def b = new Counter(4)

        expect:
            a.plus(b) == new Counter(7)
    }

    def 'operator plus works via operator syntax'() {

        given:
            def a = new Counter(3)
            def b = new Counter(4)

        expect:
            a + b == new Counter(7)
    }

    def 'operator get works via method name'() {

        given:
            def c = new Counter(10)

        expect:
            c.get(5) == 15
    }

    @PendingFeature
    def 'operator get works via subscript syntax'() {

        given:
            def c = new Counter(10)

        expect:
            c[5] == 15
    }

    def 'member infix function works via method call'() {

        given:
            def a = new Counter(3)
            def b = new Counter(4)

        expect:
            a.add(b) == new Counter(7)
    }

    def 'extension infix function works via method call'() {

        expect:
            'hello'.concatWith(' world') == 'hello world'
    }
}
