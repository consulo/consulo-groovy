import java.lang.Integer;
import java.lang.String;
import java.util.ArrayList;
import java.util.Arrays;String a = "foo";
if (org.codehaus.groovy.runtime.DefaultGroovyMethods.asBoolean(a)){
print(a);
}
 else {
print("foo foo");
ArrayList<Integer> list = new ArrayList<Integer>(Arrays.asList(1, 2, 3));
print(org.codehaus.groovy.runtime.DefaultGroovyMethods.asBoolean(list)?"full: " + java.lang.String.valueOf(list):"empty");
}

