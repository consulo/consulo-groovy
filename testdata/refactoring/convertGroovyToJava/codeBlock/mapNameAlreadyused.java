import java.lang.Integer;
import java.lang.String;
import java.util.Map;Map<String, Integer> map1 = new Map<String, Integer>(1);
map1.put("1", 2);
Map<String, Integer> map = map1;
print(map.get("1"));
