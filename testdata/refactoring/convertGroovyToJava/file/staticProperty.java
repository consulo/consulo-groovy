import java.lang.Integer;

public class Foo {
public void print() {
org.codehaus.groovy.runtime.DefaultGroovyMethods.print(this, CONST);
}

public static Integer getCONST() {
 return CONST;
}
public static void setCONST(Integer CONST) {
Foo.CONST = CONST;
}
private static Integer CONST = 5;
}
