import java.lang.Object;
import java.lang.String;

public class A {
public Object getProp() {
 return prop;
}
public void setProp(Object prop) {
this.prop = prop;
}
private Object prop;
}
public class propAssignment extends groovy.lang.Script {
public static void main(String[] args) {
new propAssignment(new groovy.lang.Binding(args)).run();
}

public Object run() {


A a = new A();

print(3 + (setProp(a, 2 + 1)));
return null;

}

public propAssignment(groovy.lang.Binding binding) {
super(binding);
}
public propAssignment() {
super();
}
private static <Value>Value setProp(A propOwner, Value prop) {
propOwner.setProp(prop);
return prop;
}
}
