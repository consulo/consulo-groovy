import java.lang.Object;
import java.lang.Override;
import java.lang.String;

public class X extends groovy.lang.GroovyObjectSupport implements groovy.lang.GroovyObject {
@Override public Object getProperty(String property) {
return "foo";
}

}
