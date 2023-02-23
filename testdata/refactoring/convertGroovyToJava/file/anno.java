import java.lang.String;

public @interface Inter {
public String foo() ;
public int de() default 4;
public String[] bar() default "1, 2, 3";
public String[] strings() ;
}
