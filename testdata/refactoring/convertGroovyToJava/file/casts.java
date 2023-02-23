import java.lang.Object;
import java.lang.String;
import java.util.Date;

public class X {
public X plus(X x) {return new X();}

}
public class casts extends groovy.lang.Script {
public static void main(String[] args) {
new casts(new groovy.lang.Binding(args)).run();
}

public Object run() {
Object a = new Date();
Date d = (Date)a;



foo((Date)a);

d = ((Date)(a));

Date b = (Date)a;

a = org.codehaus.groovy.runtime.DateGroovyMethods.plus(a, 2);



X x = new X();

x = x.plus(new X());

x = x.plus(x);

print(x);

X y = true?x:new X();
return null;

}

public void foo(Date d) {}

public casts(groovy.lang.Binding binding) {
super(binding);
}
public casts() {
super();
}
}
