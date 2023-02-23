import java.lang.Integer;
import java.lang.String;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;

public class X {
public LinkedHashMap<String, Integer> getField() {
 return field;
}
public void setField(LinkedHashMap<String, Integer> field) {
this.field = field;
}
public ArrayList<Integer> getSecond() {
 return second;
}
public void setSecond(ArrayList<Integer> second) {
this.second = second;
}
public Integer getA() {
 return a;
}
public void setA(Integer a) {
this.a = a;
}
public Integer getB() {
 return b;
}
public void setB(Integer b) {
this.b = b;
}
public LinkedHashMap<String, Integer> getC() {
 return c;
}
public void setC(LinkedHashMap<String, Integer> c) {
this.c = c;
}
public Integer getD() {
 return d;
}
public void setD(Integer d) {
this.d = d;
}
public int getX() {
 return x;
}
public void setX(int x) {
this.x = x;
}
{
LinkedHashMap<String, Integer> map = new LinkedHashMap<String, Integer>(2);
map.put("1", 2);
map.put("3", 4);
field = map;}
private LinkedHashMap<String, Integer> field;
private ArrayList<Integer> second = new ArrayList<Integer>(Arrays.asList(1, 2, 3));
private Integer a = 5;
private Integer b = 3;
{
LinkedHashMap<String, Integer> map = new LinkedHashMap<String, Integer>(1);
map.put("1", 2);
c = map;}
private LinkedHashMap<String, Integer> c;
private Integer d = 5;
private int x;
}
