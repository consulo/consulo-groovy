import java.io.File;

print(new Bar().getFoo());
print(new Bar().getBar());
Bar bar = new Bar();
print((bar == null ? null : bar.getFoo()));
File file = new File("");
print(org.codehaus.groovy.runtime.DefaultGroovyMethods.getText(file));
