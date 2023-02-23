import java.lang.Integer;
import java.lang.Object;
import java.lang.String;
import java.util.HashMap;
import java.util.Map;

public class Foo {
public void putAt(String s, Integer x, Object value) {

}

public Object getAt(String s, Integer x) {
return new Object();
}

}
public class arrayAccess extends groovy.lang.Script {
public static void main(String[] args) {
new arrayAccess(new groovy.lang.Binding(args)).run();
}

public Object run() {
HashMap<String, String> map = new HashMap<String, String>();

print(putAt0(map, "1", "6"));
print(putAt0(map, 2, "7"));
map.put("6", 1);

print(map.get("1"));
print(map.get(2));



Foo foo = new Foo();
foo.putAt("a", 2, 4);
print(putAt1(foo, "a", 2, 4));

print(foo.getAt("b", 1));
print(org.codehaus.groovy.runtime.DefaultGroovyMethods.getAt(foo, "4"));

Integer[] arr = new Integer[]{1, 2, 3};
print(arr[1]);
return arr[1] = 3;

}

public arrayAccess(groovy.lang.Binding binding) {
super(binding);
}
public arrayAccess() {
super();
}
private static <K, V, Value extends V>Value putAt0(Map<K, V> propOwner, K key, Value value) {
propOwner.put(key, value);
return value;
}
private static <Value>Value putAt1(Foo propOwner, String s, Integer x, Value value) {
propOwner.putAt(s, x, value);
return value;
}
}
