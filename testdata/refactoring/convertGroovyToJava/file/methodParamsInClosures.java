import java.lang.Integer;
import java.lang.Void;

public class X {
public Integer foo(int x) {final groovy.lang.Reference<Integer> i1 = new groovy.lang.Reference<Integer>(x);


return org.codehaus.groovy.runtime.DefaultGroovyMethods.each(1, new groovy.lang.Closure<Void>(this, this) {
public void doCall(Integer it) {
i1.set(2);
int i = 3;

}

public void doCall() {
doCall(null);
}

});
}

}
