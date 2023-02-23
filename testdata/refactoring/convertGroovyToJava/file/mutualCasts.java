import java.lang.String;
import java.util.List;

public class X {
public void foo(List l) {
if (l instanceof String){
if (l instanceof MyList){
org.codehaus.groovy.runtime.DefaultGroovyMethods.print(this, l);
String v = ((MyList)l).getValue();
}

}

}

}
public class MyList {
public String getValue() {return "";}

}
