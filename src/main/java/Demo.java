import net.plan99.nodejs.NodeJS;

public class Demo {
    public static void main(String[] args) {
        int result = NodeJS.runJS(() ->
            NodeJS.eval("return 2 + 3 + 4").asInt()
        );
        System.out.println(result);
    }
}
