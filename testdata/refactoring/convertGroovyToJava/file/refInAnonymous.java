import java.lang.Integer;
import java.lang.Runnable;

public class A {
public void foo() {
final groovy.lang.Reference<Integer> x = new groovy.lang.Reference<Integer>(2);

new Runnable(){
public void run() {
x.set(4);
org.codehaus.groovy.runtime.DefaultGroovyMethods.print(this, x.get());
}

}.run();
}

}
