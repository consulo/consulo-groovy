import java.lang.Integer;
import java.lang.Void;

public class X {
public groovy.lang.Closure<Integer> getFoo() {
 return foo;
}
public void setFoo(groovy.lang.Closure<Integer> foo) {
this.foo = foo;
}
private groovy.lang.Closure<Integer> foo = new groovy.lang.Closure<Integer>(this, this) {
public Integer doCall(int x) {final groovy.lang.Reference<Integer> i1 = new groovy.lang.Reference<Integer>(x);


return org.codehaus.groovy.runtime.DefaultGroovyMethods.each(1, new groovy.lang.Closure<Void>(X.this, X.this) {
public void doCall(Integer it) {
i1.set(2);
int i = 3;

}

public void doCall() {
doCall(null);
}

});
}

};
}
