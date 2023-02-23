import java.lang.Integer;

public class Abc {
public void foo(final int x) {
org.codehaus.groovy.runtime.DefaultGroovyMethods.times(2, new groovy.lang.Closure<Integer>(this, this) {
public Integer doCall(Integer it) {return x;}

public Integer doCall() {
return doCall(null);
}

});
}

}
