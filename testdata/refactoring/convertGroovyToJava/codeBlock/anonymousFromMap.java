import java.lang.Runnable;

print(new Runnable() {
public void run(java.lang.Object it) {print("foo}");}
public void run() {
this.run(null);
}
});
