import java.lang.Object;
import java.lang.String;

public class args extends groovy.lang.Script {
public static void main(String[] args) {
new args(new groovy.lang.Binding(args)).run();
}

public Object run() {
foo(new String[]{"a", "b", "c"});
foo(new String[]{"a"});
foo("a", 2);
foo(4);
return null;

}

public void foo(String... args) {
}

public void foo(String s, int x) {
}

public void foo(int x) {
foo("a", x);
}

public args(groovy.lang.Binding binding) {
super(binding);
}
public args() {
super();
}
}
