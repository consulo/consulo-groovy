import java.lang.Object;
import java.lang.String;
import java.util.ArrayList;

groovy.lang.Closure<Object> a = new org.codehaus.groovy.runtime.MethodClosure(new ArrayList<String>(), "add");
a.call("2");
