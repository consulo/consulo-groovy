import java.lang.Object;
import java.lang.String;

public class A {
public static Object getProp() {
 return prop;
}
public static void setProp(Object prop) {
A.prop = prop;
}
private static Object prop;
}
public class staticPropAssignment extends groovy.lang.Script {
public static void main(String[] args) {
new staticPropAssignment(new groovy.lang.Binding(args)).run();
}

public Object run() {


print(3 + (setProp(2 + 1)));
return null;

}

public staticPropAssignment(groovy.lang.Binding binding) {
super(binding);
}
public staticPropAssignment() {
super();
}
private static <Value>Value setProp(Value prop) {
A.setProp(prop);
return prop;
}
}
