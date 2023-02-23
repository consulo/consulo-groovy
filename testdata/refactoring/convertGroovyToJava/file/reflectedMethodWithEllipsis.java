import java.lang.Object;
import java.lang.String;
import java.util.LinkedHashMap;
import java.util.Map;

public class MyClass {
public void foo(Map args, String a, String... b) {}

public void foo(String a, String... b) {
foo(new LinkedHashMap<Object, Object>(), a, b);
}

}
