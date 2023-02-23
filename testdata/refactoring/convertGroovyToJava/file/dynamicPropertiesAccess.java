import java.lang.String;

public class A {
public void foo() {
setProperty("bar", 2);
org.codehaus.groovy.runtime.DefaultGroovyMethods.print(this, setProperty0(this, "bar", 3));
String s = "a";
s.bar = 4;
org.codehaus.groovy.runtime.DefaultGroovyMethods.print(this, s.bar = 5);

org.codehaus.groovy.runtime.DefaultGroovyMethods.print(this, getProperty("bar"));
org.codehaus.groovy.runtime.DefaultGroovyMethods.print(this, s.bar);
}

private static <Value>Value setProperty0(groovy.lang.GroovyObjectSupport propOwner, String property, Value newValue) {
propOwner.setProperty(property, newValue);
return newValue;
}
}
