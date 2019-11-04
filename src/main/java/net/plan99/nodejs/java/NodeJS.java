package net.plan99.nodejs.java;

import net.plan99.nodejs.kotlin.NodeJSAPI;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.TypeLiteral;
import org.graalvm.polyglot.Value;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.function.Supplier;
import java.util.jar.JarInputStream;

/**
 * Provides an interface to the NodeJS runtime for Java developers. You can only access the NodeJS world
 * when running on the event loop thread, which means you must use the various runJS methods on this class
 * to get onto the correct thread before using eval.
 */
@SuppressWarnings("WeakerAccess")
public class NodeJS {
    private static class Linkage {
        LinkedBlockingDeque<Runnable> taskQueue;
        Value evalFunction;
        Thread nodeJSThread;
        Context ctx;

        Linkage(LinkedBlockingDeque<Runnable> taskQueue, Value evalFunction) {
            this.taskQueue = taskQueue;
            this.evalFunction = evalFunction;
            this.nodeJSThread = Thread.currentThread();
            this.ctx = Context.getCurrent();
        }
    }

    private volatile static Linkage linkage;

    // Called from the boot.js file as part of NodeJVM startup, do not call.
    @SuppressWarnings("unused")
    @Deprecated
    public static void boot(LinkedBlockingDeque<Runnable> taskQueue,
                            Value evalFunction,
                            String[] args) throws ClassNotFoundException, IOException {
        try {
            boot1(taskQueue, evalFunction, args);
        } catch (Throwable e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void boot1(LinkedBlockingDeque<Runnable> taskQueue, Value evalFunction, String[] args) throws ClassNotFoundException, IOException {
        assert linkage == null : "Don't call this function directly. Already started!";
        assert evalFunction.canExecute();
        NodeJS.linkage = new Linkage(taskQueue, evalFunction);
        Thread.currentThread().setName("NodeJS main thread");

        if (args.length == 0) {
            System.err.println("You must specify at least a class name, or -jar jarname.jar");
            System.exit(1);
        } else if (!args[0].equals("-jar")) {
            Class<?> entryPoint = Class.forName(args[0]);
            startJavaThread(entryPoint, Arrays.copyOfRange(args, 1, args.length));
        } else {
            File myJar = new File(args[1]);
            final URL url = myJar.toURI().toURL();
            String mainClassName = "";
            try (InputStream stream = Files.newInputStream(Paths.get(args[1]), StandardOpenOption.READ)) {
                JarInputStream jis = new JarInputStream(stream);
                mainClassName = jis.getManifest().getMainAttributes().getValue("Main-Class");
            }

            if (mainClassName == null) {
                System.err.println("JAR file does not have a Main-Class attribute, is not executable.");
                System.exit(1);
            }
            // Use the parent classloader to forcibly toss out this version of the interop JAR, to avoid confusion
            // later when there are two classloaders in play, BUT, holepunch this specific class and inner classes
            // through so the state linked up from the bootstrap script is still here. In other words, this class is
            // special, so don't give it dependencies outside the JDK.
            ClassLoader thisClassLoader = NodeJS.class.getClassLoader();
            URLClassLoader child = new URLClassLoader(new URL[] {url}, thisClassLoader.getParent()) {
                @Override
                protected Class<?> findClass(String name) throws ClassNotFoundException {
                    if (name.startsWith(NodeJS.class.getName())) {
                        return thisClassLoader.loadClass(name);
                    } else
                        return super.findClass(name);
                }
            };
            Class<?> entryPoint = Class.forName(mainClassName, true, child);
            startJavaThread(entryPoint, Arrays.copyOfRange(args, 2, args.length));
        }
    }

    private static void startJavaThread(Class<?> entryPoint, String[] args) {
        Thread javaThread = new Thread(() -> {
            try {
                Method main = entryPoint.getMethod("main", String[].class);
                assert Modifier.isStatic(main.getModifiers());
                main.invoke(null, new Object[] { args });
                System.exit(0);
            } catch (NoSuchMethodException e) {
                System.err.println("No main method found in " + entryPoint.getName());
            } catch (InvocationTargetException e) {
                e.getCause().printStackTrace();
            } catch (Throwable e) {
                e.printStackTrace();
            }
            System.exit(1);
        }, "Java main thread");
        javaThread.start();
    }

    /**
     * Returns true if executing within the main NodeJS thread. Note: does NOT return true if you are executing on
     * some other worker you created yourself.
     */
    public static boolean isOnMainNodeThread() {
        return Thread.currentThread() == linkage.nodeJSThread;
    }

    /**
     * Throws {@link IllegalStateException} if you aren't on the NodeJS main thread.
     */
    public static void checkOnMainNodeThread() {
        if (!isOnMainNodeThread())
            throw new IllegalStateException("You are not currently on the NodeJS thread and thus cannot access the JavaScript world. Surround your calls to JS with NodeJS.runJS(), NodeJS.runJSAsync() or the Kotlin nodejs{} block.");
    }

    /**
     * This {@link Executor} runs the given commands on the NodeJS event loop thread. The other utility functions
     * on this class are all just utilities that delegate to this executor.
     */
    public static Executor executor = new Executor() {
        @Override
        public void execute(@NotNull Runnable command) {
            if (linkage == null)
                throw new IllegalStateException("This JVM was not started with the nodejvm script.");

            if (Thread.currentThread() == linkage.nodeJSThread)
                command.run();
            else
                linkage.taskQueue.add(command);
        }
    };

    /**
     * Schedules execution of the given {@link Supplier} onto the NodeJS thread, and returns a future that will
     * complete when it's been run.
     */
    public static <T> CompletableFuture<T> runJSAsync(Supplier<T> callable) {
        return CompletableFuture.supplyAsync(callable, executor);
    }

    /**
     * Runs the given {@link Supplier} on the NodeJS thread, blocks until execution has been performed and
     * returns the result that was computed.
     */
    public static <T> T runJS(Supplier<T> callable) {
        try {
            return runJSAsync(callable).get();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException)
                throw (RuntimeException) e.getCause();
            else
                throw new RuntimeException(e.getCause());
        }
    }

    /**
     * Evaluates the given string and returns a generic {@link Value} object representing the result, that can then
     * be converted into more useful forms. Note that you must be on the NodeJS thread for this to work (see
     * {@link #runJS(Supplier)} for how to do this).
     *
     * @throws IllegalStateException if you're not on the NodeJS thread.
     */
    public static Value eval(@Language("JavaScript") String js) {
        checkOnMainNodeThread();
        return linkage.evalFunction.execute(js);
    }

    /**
     * Returns the {@link Context} for the NodeJS main thread.
     */
    public static Context polyglotContext() {
        return linkage.ctx;
    }

    /**
     * Converts the {@link Value} to a JVM type in the following way:<p>
     *
     * <ol>
     * <li> If the type is an interface not annotated with {@code @FunctionalInterface} then a special proxy is returned that
     *    knows how to map JavaBean style property methods on that interface to JavaScript properties.</li>
     * <li> Otherwise, the {@link Value#as} method is used with the {@link TypeLiteral} so generics are preserved and
     *    the best possible translation occurs.</li>
     * </ol>
     */
    public static <T> T castValue(Value value, TypeLiteral<T> typeLiteral) {
        return NodeJSAPI.castValue(value, typeLiteral);
    }
}