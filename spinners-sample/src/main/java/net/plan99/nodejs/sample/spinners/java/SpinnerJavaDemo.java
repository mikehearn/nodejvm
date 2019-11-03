package net.plan99.nodejs.sample.spinners.java;

import net.plan99.nodejs.NodeJS;
import org.graalvm.polyglot.TypeLiteral;
import org.graalvm.polyglot.Value;

public class SpinnerJavaDemo {
    interface Ora {
        void start(String text);
    }
    public static void main(String[] args) throws InterruptedException {
        NodeJS.executor.execute(() -> {
            Value v = NodeJS.eval("require('ora')()");
            Ora ora = NodeJS.castValue(v, new TypeLiteral<Ora>() {});
            ora.start("Hello world!");
        });
        Thread.sleep(2000);
    }
}
