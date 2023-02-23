import java.lang.Integer;
import java.lang.Object;
import java.lang.String;

public class return2 extends groovy.lang.Script {
public static void main(String[] args) {
new return2(new groovy.lang.Binding(args)).run();
}

public Object run() {

return null;

}

public boolean foo() {
Integer a = 5;

org.codehaus.groovy.runtime.DefaultGroovyMethods.times(a, new groovy.lang.Closure<Integer>(this, this) {
public Integer doCall(Integer it) {
if (it == 2)return 5;
}

public Integer doCall() {
return doCall(null);
}

});

}

public return2(groovy.lang.Binding binding) {
super(binding);
}
public return2() {
super();
}
}
