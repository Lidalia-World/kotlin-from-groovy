package uk.org.lidalia.kotlinfromgroovy.testsupport

class GroovyBaseClassWithPrivateMethod {

    String callPrivateMethod(String input) {
        privateHelper(input)
    }

    private String privateHelper(String input) {
        "processed: $input"
    }
}
