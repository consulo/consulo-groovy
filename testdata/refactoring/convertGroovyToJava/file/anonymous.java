import java.lang.Integer;
import java.lang.Object;
import java.lang.Runnable;
import java.lang.String;

public class anonymous extends groovy.lang.Script {
public static void main(String[] args) {
new anonymous(new groovy.lang.Binding(args)).run();
}

public Object run() {
final Integer foo = 2;
Runnable an = new Runnable(){
public void run() {
org.codehaus.groovy.runtime.DefaultGroovyMethods.println(this, foo);
}

};

an.run();

println(foo);
return null;

}

public anonymous(groovy.lang.Binding binding) {
super(binding);
}
public anonymous() {
super();
}
}
