import java.lang.Integer;
import java.lang.Void;
import java.util.ArrayList;
import java.util.Arrays;ArrayList<Integer> list = new ArrayList<Integer>(Arrays.asList(1, 2, 3));
org.codehaus.groovy.runtime.DefaultGroovyMethods.each(list, new groovy.lang.Closure<Void>(this, this) {
public void doCall(Integer it) {
print(it);
}

public void doCall() {
doCall(null);
}

});
