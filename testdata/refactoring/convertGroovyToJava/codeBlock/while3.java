import java.lang.Integer;
import java.util.ArrayList;
import java.util.List;List<Integer> list = new ArrayList<Integer>(3);
list.add(1);
list.add(2);
list.add(3);
List<Integer> list1 = new ArrayList<Integer>(2);
list1.add(3);
list1.add(4);
while (!org.codehaus.groovy.runtime.DefaultGroovyMethods.minus(list, list1).isEmpty()) list = org.codehaus.groovy.runtime.DefaultGroovyMethods.minus(list, 1);
