import java.lang.Integer;
import java.lang.Object;

public class X {
public void foo() {
final groovy.lang.Reference<Integer> ab = new groovy.lang.Reference<Integer>(4);

org.codehaus.groovy.runtime.DefaultGroovyMethods.each(this, new groovy.lang.Closure<X>(this, this) {
public X doCall(Object it) {
return org.codehaus.groovy.runtime.DefaultGroovyMethods.each(X.this, new groovy.lang.Closure<Integer>(X.this, X.this) {
public Integer doCall(Object it) {
return setGroovyRef(ab, 2);

}

public Integer doCall() {
return doCall(null);
}

});
}

public X doCall() {
return doCall(null);
}

});

org.codehaus.groovy.runtime.DefaultGroovyMethods.print(this, ab.get());
}

private static <T> T setGroovyRef(groovy.lang.Reference<T> ref, T newValue) {
ref.set(newValue);
return newValue;
}}
