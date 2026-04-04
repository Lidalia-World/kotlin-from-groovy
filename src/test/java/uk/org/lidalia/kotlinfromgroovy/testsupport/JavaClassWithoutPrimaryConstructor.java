package uk.org.lidalia.kotlinfromgroovy.testsupport;

public class JavaClassWithoutPrimaryConstructor {

  private String value;

  public JavaClassWithoutPrimaryConstructor() {
    this.value = "default";
  }

  public JavaClassWithoutPrimaryConstructor(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }
}
