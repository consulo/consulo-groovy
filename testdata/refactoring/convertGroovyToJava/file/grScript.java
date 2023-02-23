import java.lang.Integer;
import java.lang.Object;
import java.lang.String;

public class grScript extends groovy.lang.Script {
public static void main(String[] args) {
new grScript(new groovy.lang.Binding(args)).run();
}

public Object run() {
print("foo");
if (true){
print("false");
}
 else {
print("true");
Integer a = 5;
}

return null;

}

public grScript(groovy.lang.Binding binding) {
super(binding);
}
public grScript() {
super();
}
}
