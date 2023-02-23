@p.I package p;

import java.lang.Integer;
import java.lang.Object;
import java.lang.String;

public @interface I {
public int x() ;
}
@p.I public class A {
@p.I public A() {}
}
public class useAnno extends groovy.lang.Script {
public static void main(String[] args) {
new p.useAnno(new groovy.lang.Binding(args)).run();
}

public Object run() {




@p.I public Integer var = 3;






return null;

}

@p.I public void foo(@p.I final Object x) {
@p.I Object a;
}

public useAnno(groovy.lang.Binding binding) {
super(binding);
}
public useAnno() {
super();
}
}
