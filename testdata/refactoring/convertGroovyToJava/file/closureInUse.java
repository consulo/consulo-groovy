import java.lang.Integer;
import java.lang.Object;
import java.lang.String;
import java.lang.Void;

public class IntCat {
public static void call(Integer i) {org.codehaus.groovy.runtime.DefaultGroovyMethods.print(this, i);}

public static void call(Integer i, String s) {org.codehaus.groovy.runtime.DefaultGroovyMethods.print(this, s);}

}
public class closureInUse extends groovy.lang.Script {
public static void main(String[] args) {
new closureInUse(new groovy.lang.Binding(args)).run();
}

public Object run() {


return org.codehaus.groovy.runtime.DefaultGroovyMethods.use(this, IntCat.class, new groovy.lang.Closure<Void>(this, this) {
public void doCall(Object it) {
IntCat.call(2);
IntCat.call(2, "a");
IntCat.call(2);
IntCat.call(2, "2");
}

public void doCall() {
doCall(null);
}

});
}

public closureInUse(groovy.lang.Binding binding) {
super(binding);
}
public closureInUse() {
super();
}
}
