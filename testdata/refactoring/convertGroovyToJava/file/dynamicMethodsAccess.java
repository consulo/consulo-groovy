import java.lang.Integer;
import java.lang.Object;
import java.lang.String;
import java.util.ArrayList;
import java.util.LinkedHashMap;

public class Abc {
public Object foo() {
invokeMethod("bar", new Object[]{2});
LinkedHashMap<String, Integer> map = new LinkedHashMap<String, Integer>(1);
map.put("s", 4);
org.codehaus.groovy.runtime.DefaultGroovyMethods.print(this, invokeMethod("bar", new Object[]{map, 3}));
String s = "a";
org.codehaus.groovy.runtime.DefaultGroovyMethods.invokeMethod(s, "bar", new Object[]{4});
org.codehaus.groovy.runtime.DefaultGroovyMethods.print(this, org.codehaus.groovy.runtime.DefaultGroovyMethods.invokeMethod(s, "bar", new Object[]{5}));

return org.codehaus.groovy.runtime.DefaultGroovyMethods.invokeMethod(s, "anme", new ArrayList<Object>());
}

}
