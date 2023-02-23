import java.lang.Object;
import java.lang.String;

public class A {
public int getProp() {
 return prop;
}
public void setProp(int prop) {
this.prop = prop;
}
private int prop;
}
public class intPropAssignment extends groovy.lang.Script {
public static void main(String[] args) {
new intPropAssignment(new groovy.lang.Binding(args)).run();
}

public Object run() {


A a = new A();

print(3 + (setProp(a, 2 + 1)));
return null;

}

public intPropAssignment(groovy.lang.Binding binding) {
super(binding);
}
public intPropAssignment() {
super();
}
private static int setProp(A propOwner, int prop) {
propOwner.setProp(prop);
return prop;
}
}
