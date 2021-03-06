// Test case for Issue 408:
// https://code.google.com/p/checker-framework/issues/detail?id=408
//@skip-test

import org.checkerframework.checker.initialization.qual.UnderInitialization;

public class Issue408 {
  static class Bar {
    Bar() {
      doFoo();
    }

    String doFoo(@UnderInitialization Bar this) {
      return "";
    }
  }

  static class Baz extends Bar {
    String myString = "hello";

    @Override
    String doFoo(@UnderInitialization Baz this) {
      return myString.toLowerCase();
    }
  }

  public static void main(String[] args) {
    new Baz();
  }
}
