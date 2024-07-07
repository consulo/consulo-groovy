import java.lang.Integer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;Date date = new Date(2011, 04, 09);
if (org.codehaus.groovy.runtime.DefaultGroovyMethods.isCase(new Date(20, 11, 23), date)) {
print("aaa");
print("bbb");
}
else if (org.codehaus.groovy.runtime.DefaultGroovyMethods.isCase(new ArrayList<Integer>(Arrays.asList(1)), date)) {
print("bbb");
}
else {
print("ccc");
}
