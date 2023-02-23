import java.io.File;
import java.lang.Byte;
import java.lang.Exception;
import java.lang.Object;
import java.lang.String;
import java.lang.System;
import java.lang.Void;

public class BigInteger {
public BigInteger(int i, Byte[] arr) {}
public String toString(int radix) {return "";}

}
public class NoSuchAlgorithmException extends Exception {
}
public class MessageDigest {
public static MessageDigest getInstance(String algorithm) throws NoSuchAlgorithmException {
return new MessageDigest();
}

public Byte[] digest() {return new Byte[0];}

public void update(Byte[] input, int offset, int len) {}

}
public class hash extends groovy.lang.Script {
public static void main(String[] args) {
new hash(new groovy.lang.Binding(args)).run();
}

public Object run() {







int KB = 1024;
int MB = 1024 * KB;

File f = new File(this.getBinding().getProperty("args")[0]);
if (!f.exists() || !f.isFile()){
println("Invalid file " + String.valueOf(f) + " provided");
println("Usage: groovy sha1.groovy <file_to_hash>");
}


final MessageDigest messageDigest = MessageDigest.getInstance("SHA1");

long start = System.currentTimeMillis();

org.codehaus.groovy.runtime.DefaultGroovyMethods.eachByte(f, MB, new groovy.lang.Closure<Void>(this, this) {
public void doCall(Byte[] buf, int bytesRead) {
messageDigest.update(buf, 0, bytesRead);
}

});

String sha1Hex = org.codehaus.groovy.runtime.DefaultGroovyMethods.padLeft(new BigInteger(1, messageDigest.digest()).toString(16), 40, "0");
long delta = System.currentTimeMillis() - start;

println(sha1Hex + " took " + String.valueOf(delta) + " ms to calculate");
return null;

}

public hash(groovy.lang.Binding binding) {
super(binding);
}
public hash() {
super();
}
}
