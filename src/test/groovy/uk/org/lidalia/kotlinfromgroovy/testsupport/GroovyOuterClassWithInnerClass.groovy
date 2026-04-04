package uk.org.lidalia.kotlinfromgroovy.testsupport

import groovy.transform.Immutable

class GroovyOuterClassWithInnerClass {

    @Immutable
    class Inner {
        String name
        int value
    }

    Inner createInner(String name, int value) {
        new Inner(name: name, value: value)
    }
}
