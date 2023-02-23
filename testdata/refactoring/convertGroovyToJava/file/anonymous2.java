import java.lang.Object;
import java.lang.String;

public abstract class Anon {
public Anon(Object foo) {
this.foo = foo;
}
public abstract void run() ;
public Object getFoo() {
 return foo;
}
public void setFoo(Object foo) {
this.foo = foo;
}
private Object foo;
}
public class anonymous2 extends groovy.lang.Script {
public static void main(String[] args) {
new anonymous2(new groovy.lang.Binding(args)).run();
}

public Object run() {
Anon an = new Anon(3){
public void run() {
org.codehaus.groovy.runtime.DefaultGroovyMethods.println(this, getFoo());
}

};

an.run();

return null;

}

public anonymous2(groovy.lang.Binding binding) {
super(binding);
}
public anonymous2() {
super();
}
}
