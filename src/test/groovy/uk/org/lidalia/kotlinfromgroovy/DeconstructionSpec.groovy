package uk.org.lidalia.kotlinfromgroovy

import kotlin.Pair
import org.codehaus.groovy.runtime.typehandling.GroovyCastException
import spock.lang.PendingFeature
import spock.lang.Specification
import uk.org.lidalia.kotlinfromgroovy.testsupport.ClassWithComponents
import uk.org.lidalia.kotlinfromgroovy.testsupport.DataClass

class DeconstructionSpec extends Specification {

    def 'can deconstruct a pair'() {

        when:
            def (first, second) = new Pair('argument1', 2)

        then:
            first == 'argument1'
            second == 2
    }

    def 'can deconstruct a data class'() {

        when:
            def (first, second, third) = new DataClass("argument1", 2, true)

        then:
            first == 'argument1'
            second == 2
            third == true
    }

    def 'can partially deconstruct a class'() {

        when:
            def (first, second) = new DataClass("argument1", 2, true)

        then:
            first == 'argument1'
            second == 2
    }

    def 'can deconstruct a normal class with components'() {

        when:
            def (first, second) = new ClassWithComponents("argument1", 2, true)

        then:
            first == 'argument1'
            second == 2
    }

    def 'cannot deconstruct when too many components'() {

        when:
            def (first, second, third) = new ClassWithComponents("argument1", 2, true)

        then:
            def exception = thrown(IllegalArgumentException)
            exception.message == 'Destructuring declaration initializer of type ClassWithComponents must have a \'component3()\' function'
    }

    @PendingFeature
    def 'typed deconstruction works'() {

        given:
            def (String first, Integer second) = new ClassWithComponents('argument1', 2, true)

        expect:
            first == 'argument1'
            second == 2
    }

    @PendingFeature
    def 'typed deconstruction fails for wrong type'() {

        when:
            def (String first, String second) = new ClassWithComponents('argument1', 2, true)

        then:
            def exception = thrown(GroovyCastException)
            exception.message == 'Cannot cast object 2 with class \'java.lang.Integer\' to class \'java.lang.String\''
    }
}
