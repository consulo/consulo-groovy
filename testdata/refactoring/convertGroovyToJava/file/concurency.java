import java.lang.Integer;
import java.lang.Object;
import java.lang.String;
import java.lang.Thread;
import java.lang.Void;
import java.util.concurrent.atomic.AtomicInteger;

public class concurency extends groovy.lang.Script {
public static void main(String[] args) {
new concurency(new groovy.lang.Binding(args)).run();
}

public Object run() {


final AtomicInteger counter = new AtomicInteger();



Thread th = org.codehaus.groovy.runtime.DefaultGroovyStaticMethods.start(null, new groovy.lang.Closure<Void>(this, this) {
public void doCall(Object it) {
for(Integer i : new groovy.lang.IntRange(1, 8)){
org.codehaus.groovy.runtime.DefaultGroovyStaticMethods.sleep(null, 30);
out("thread loop " + String.valueOf(i));
counter.incrementAndGet();
}

}

public void doCall() {
doCall(null);
}

});

for(Integer j : new groovy.lang.IntRange(1, 4)){
org.codehaus.groovy.runtime.DefaultGroovyStaticMethods.sleep(null, 50);
out("main loop " + String.valueOf(j));
counter.incrementAndGet();
}


th.join();

assert counter.get() == 12;
return null;

}

public synchronized void out(groovy.lang.GString message) {
println(message);
}

public concurency(groovy.lang.Binding binding) {
super(binding);
}
public concurency() {
super();
}
}
