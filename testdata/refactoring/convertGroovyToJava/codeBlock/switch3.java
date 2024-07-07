import java.util.Date;Date date = new Date(2011, 04, 09);
if (org.codehaus.groovy.runtime.DefaultGroovyMethods.isCase(new Date(20, 11, 23), date)) {
print("aaa");
print("bbb");
}
else if (org.codehaus.groovy.runtime.DefaultGroovyMethods.isCase(new Date(45, 1, 2), date)) {
print("bbb");
}
else {
print("ccc");
}
