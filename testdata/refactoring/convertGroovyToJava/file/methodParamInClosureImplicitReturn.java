import java.lang.Integer;
import java.lang.Object;
import java.lang.String;
import java.util.ArrayList;
import java.util.Arrays;

public class methodParamInClosureImplicitReturn extends groovy.lang.Script {
public static void main(String[] args) {
new methodParamInClosureImplicitReturn(new groovy.lang.Binding(args)).run();
}

public Object run() {

return null;

}

public void foo(int x) {final groovy.lang.Reference<Integer> i = new groovy.lang.Reference<Integer>(x);

org.codehaus.groovy.runtime.DefaultGroovyMethods.each(new ArrayList<Integer>(Arrays.asList(1, 2, 3)), new groovy.lang.Closure<Integer>(this, this) {
public Integer doCall(Integer it) {
print(i.get());
return setGroovyRef(i, i.get() + 1);
}

public Integer doCall() {
return doCall(null);
}

});

org.codehaus.groovy.runtime.DefaultGroovyMethods.each(new ArrayList<Integer>(Arrays.asList(1, 2, 3)), new groovy.lang.Closure<Integer>(this, this) {
public Integer doCall(Integer it) {
print(i.get());
i.set(i.get()++);
return i.get();
}

public Integer doCall() {
return doCall(null);
}

});

print(i.get());
}

public methodParamInClosureImplicitReturn(groovy.lang.Binding binding) {
super(binding);
}
public methodParamInClosureImplicitReturn() {
super();
}
private static <T> T setGroovyRef(groovy.lang.Reference<T> ref, T newValue) {
ref.set(newValue);
return newValue;
}}
