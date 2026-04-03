package uk.org.lidalia.kotlinfromgroovy

import spock.lang.PendingFeature
import spock.lang.Specification
import uk.org.lidalia.kotlinfromgroovy.testsupport.DataClass
import uk.org.lidalia.kotlinfromgroovy.testsupport.DataClassWithCustomCopyMethod

class DataClassCopySpec extends Specification {

    def dataClassInstance = new DataClass('argument1', 2, true)

    def 'can call copy using named arguments'() {

        // Deliberately not using a parameterised test here as the AST transform may need the map inline
        expect:
            dataClassInstance.copy(argument1: 'new arg') == new DataClass('new arg', 2, true)
            dataClassInstance.copy(argument2: 22) == new DataClass('argument1', 22, true)
            dataClassInstance.copy(argument3: false) == new DataClass('argument1', 2, false)
            dataClassInstance.copy(argument1: 'new arg', argument2: 22) == new DataClass('new arg', 22, true)
            dataClassInstance.copy(argument1: 'new arg', argument3: false) == new DataClass('new arg', 2, false)
            dataClassInstance.copy(argument2: 22, argument3: false) == new DataClass('argument1', 22, false)
            dataClassInstance.copy(argument1: 'new arg', argument2: 22, argument3: false) == new DataClass('new arg', 22, false)
    }

    def 'can call copy using positional arguments'() {

        expect:
            dataClassInstance.copy() == new DataClass('argument1', 2, true)
            dataClassInstance.copy('new arg') == new DataClass('new arg', 2, true)
            dataClassInstance.copy('new arg', 22) == new DataClass('new arg', 22, true)
            dataClassInstance.copy('new arg', 22, false) == new DataClass('new arg', 22, false)
    }

    def 'can call copy on a class with a custom copy method'() {

        expect:
            new DataClassWithCustomCopyMethod('argument1', 2).copy('new arg') == new DataClassWithCustomCopyMethod('new arg', 5)
            new DataClassWithCustomCopyMethod('argument1', 2).copy('new arg', 3) == new DataClassWithCustomCopyMethod('new arg', 3)
            new DataClassWithCustomCopyMethod('argument1', 2).copy(argument1: 'new arg') == new DataClassWithCustomCopyMethod('new arg', 5)
            new DataClassWithCustomCopyMethod('argument1', 2).copy(argument2: 3) == new DataClassWithCustomCopyMethod('argument1', 3)
    }

    def 'cannot call copy with wrong named argument'() {

        when:
            dataClassInstance.copy(notThere: 'new arg')

        then:
            def exception = thrown(IllegalArgumentException)
            exception.message == 'Cannot find a parameter with this name: notThere'
    }

    def 'cannot call copy with wrong type of argument'() {

        when:
            dataClassInstance.copy(argument1: 2)

        then:
            def exception = thrown(IllegalArgumentException)
            exception.message == 'The integer literal does not conform to the expected type String'
    }

    def 'cannot call copy using positional arguments with wrong type of argument'() {

        when:
            dataClassInstance.copy(2)

        then:
            def exception = thrown(IllegalArgumentException)
            exception.message == 'The integer literal does not conform to the expected type String'
    }
}
