import java.lang.Integer;
import java.lang.Object;
import java.lang.String;
import java.lang.Void;

public class refInClosureInScript extends groovy.lang.Script {
public static void main(String[] args) {
new refInClosureInScript(new groovy.lang.Binding(args)).run();
}

public Object run() {
final groovy.lang.Reference<Integer> foo = new groovy.lang.Reference<Integer>(2);

org.codehaus.groovy.runtime.DefaultGroovyMethods.times(3, new groovy.lang.Closure<Void>(this, this) {
public void doCall(Integer it) {
foo.set(foo.get()++);
foo.set(foo.get() + 2);
foo.set(foo.get() - 1);
foo.set(4);
print(foo.get());
}

public void doCall() {
doCall(null);
}

});
return null;

}

public refInClosureInScript(groovy.lang.Binding binding) {
super(binding);
}
public refInClosureInScript() {
super();
}
}
