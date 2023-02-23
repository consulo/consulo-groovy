import java.io.IOException;

class Foo {
  public static void main(String[] args) {
      try {
          new AddException().foo();
      } catch (IOException e) {
          e.printStackTrace();
      }
  }
}