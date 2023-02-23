import java.lang.Integer;
import java.lang.Object;
import java.lang.String;
import java.util.ArrayList;
import java.util.Arrays;

public class resolveMethodInsideClosure extends groovy.lang.Script {
public static void main(String[] args) {
new resolveMethodInsideClosure(new groovy.lang.Binding(args)).run();
}

public Object run() {

return null;

}

public ArrayList<Integer> foo() {
ArrayList<Integer> list = new ArrayList<Integer>(Arrays.asList(1, 2, 3));
return org.codehaus.groovy.runtime.DefaultGroovyMethods.each(list, new groovy.lang.Closure<ArrayList<Integer>>(this, this) {
public ArrayList<Integer> doCall(Integer it) {
return foo();
}

public ArrayList<Integer> doCall() {
return doCall(null);
}

});
}

public resolveMethodInsideClosure(groovy.lang.Binding binding) {
super(binding);
}
public resolveMethodInsideClosure() {
super();
}
}
