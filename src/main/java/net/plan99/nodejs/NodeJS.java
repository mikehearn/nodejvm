package net.plan99.nodejs;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.function.Supplier;

/**
 * Provides an interface to the NodeJS runtime. You can only access the NodeJS world when running on the
 * event loop thread, which means you must use the various runJS methods on this class to get onto the
 * correct thread before using eval.
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

    /**
     * Called from the boot.js file and runs on the net.plan99.nodejs.NodeJS event loop thread.
     */
    @SuppressWarnings("unused")
    public static void boot(String entryPointName,
                            LinkedBlockingDeque<Runnable> taskQueue,
                            Value evalFunction,
                            String[] args) {
        assert linkage == null : "Don't call this function directly. Already started!";
        assert evalFunction.canExecute();
        NodeJS.linkage = new Linkage(taskQueue, evalFunction);
        Thread.currentThread().setName("NodeJS main thread");
        Thread javaThread = new Thread(() -> {
            try {
                Class<?> entryPoint = Class.forName(entryPointName);
                Method main = entryPoint.getMethod("main", String[].class);
                assert Modifier.isStatic(main.getModifiers());
                main.invoke(null, new Object[] { args });
                System.exit(0);
            } catch (NoSuchMethodException e) {
                System.err.println("No main method found in " + entryPointName);
            } catch (InvocationTargetException e) {
                e.getCause().printStackTrace();
            } catch (ClassNotFoundException e) {
                System.err.println(String.format("Main class with name '%s' not found.", entryPointName));
            } catch (Throwable e) {
                e.printStackTrace();
            }
            System.exit(1);
        }, "Java main thread");
        javaThread.start();
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
        if (linkage.nodeJSThread != Thread.currentThread())
            throw new IllegalStateException("You can only runJS NodeJS.eval() when on the main NodeJS thread, use runJSAsync or runJS and use this inside the lambda.");
        return linkage.evalFunction.execute(js);
    }

    /**
     * Returns the {@link Context} for the NodeJS main thread.
     */
    public static Context polyglotContext() {
        return linkage.ctx;
    }
}