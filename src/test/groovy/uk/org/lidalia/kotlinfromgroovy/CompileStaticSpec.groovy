package uk.org.lidalia.kotlinfromgroovy

import groovy.transform.CompileStatic
import org.junit.jupiter.api.Test
import uk.org.lidalia.kotlinfromgroovy.testsupport.ClassWithDefaultProperties

@CompileStatic
class CompileStaticSpec {

    @Test
    void 'can call Java standard library methods in @CompileStatic context'() {
        assert 'hello'.toUpperCase() == 'HELLO'
        assert [1, 2, 3].size() == 3
    }

    @Test
    void 'can construct Kotlin class with positional args in @CompileStatic context'() {
        def instance = new ClassWithDefaultProperties('arg1', 'arg2')
        assert instance.argument1 == 'arg1'
        assert instance.argument2 == 'arg2'
    }
}
