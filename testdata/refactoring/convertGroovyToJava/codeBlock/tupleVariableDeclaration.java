import java.io.Serializable;
import java.lang.Integer;
import java.lang.Object;
import java.lang.String;
import java.util.Arrays;
import java.util.Iterator;

String s = "1";
int x = 2;

String s = "1";
Integer x = 2;

final Iterator<Serializable> iterator = Arrays.asList("2", 1).iterator();
String s = ((String)(iterator.hasNext() ? iterator.next() : null));
Object x = iterator.hasNext() ? iterator.next() : null;

