package uk.org.lidalia.kotlinfromgroovy.testsupport;

import java.util.Locale;

public class JavaClassWithOverloadedConstructors {

    public final String type;
    public final String value;

    public JavaClassWithOverloadedConstructors(String type, String value) {
        this.type = type;
        this.value = value;
    }

    public JavaClassWithOverloadedConstructors(String type, Locale locale) {
        this.type = type;
        this.value = locale != null ? locale.toString() : null;
    }
}
