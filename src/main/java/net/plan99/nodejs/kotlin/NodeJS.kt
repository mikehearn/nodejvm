@file:Suppress("UNUSED_VARIABLE")

package net.plan99.nodejs.kotlin

import net.plan99.nodejs.NodeJS
import org.graalvm.polyglot.TypeLiteral
import org.graalvm.polyglot.Value
import org.intellij.lang.annotations.Language
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import kotlin.reflect.KProperty

/**
 * Enters the NodeJS event loop thread and makes the JavaScript API available in scope. You can return JavaScript
 * objects from a nodejs block, however, you can't access them again until you're back inside. Attempting to use
 * them outside a nodejs block will trigger a threading violation exception.
 *
 * Note that this function schedules the lambda onto the NodeJS event loop and waits for it to be executed. If Node
 * is busy, it'll block and wait for it.
 */
fun <T> nodejs(body: NodeJSBlock.() -> T): T = NodeJS.runJS { body(NodeJSBlock()) }

/**
 * Defines various Kotlin helpers to make working with the JavaScript environment more pleasant. Intended to be used
 * inside a [nodejs] block.
 */
class NodeJSBlock internal constructor() {
    /**
     * Converts the [Value] to a JVM type [T] in the following way:
     *
     * 1. If the type is an interface not annotated with `@FunctionalInterface` then a special proxy is returned that
     *    knows how to map JavaBean style property methods on that interface to JavaScript properties.
     * 2. Otherwise, the [Value. as] method is used with a [TypeLiteral] so generics are preserved and the best possible
     *    translation occurs.
     */
    inline fun <reified T> Value.cast(): T = castValue(this, object : TypeLiteral<T>() {})

    companion object {
        /** @see Value.cast */
        @JvmStatic
        fun <T> castValue(value: Value, typeLiteral: TypeLiteral<T>): T {
            val clazz = typeLiteral.rawType
            @Suppress("UNCHECKED_CAST")
            return if (JSTranslationProxyHandler.isTranslateableInterface(clazz))
                Proxy.newProxyInstance(clazz.classLoader, arrayOf(clazz, *clazz.interfaces), JSTranslationProxyHandler(value)) as T
            else
                value.`as`(typeLiteral)
        }
    }

    /** Casts any object to being a JavaScript object. */
    fun Any.asValue(): Value = NodeJS.polyglotContext().asValue(this)

    /**
     * Evaluates the given JavaScript string and casts the result to the desired JVM type. You can request a cast
     * to interfaces that map to JS objects, collections, the Graal/Polyglot [Value] type, boxed primitives and more.
     */
    inline fun <reified T> eval(@Language("JavaScript") javascript: String): T = NodeJS.eval(javascript).cast()

    /**
     * Evaluates the given JavaScript but throws away any result.
     */
    fun run(@Language("JavaScript") javascript: String) {
        NodeJS.eval(javascript)
    }

    /** Allows you to read JS properties of the given [Value] using Kotlin indexing syntax. */
    inline operator fun <reified T> Value.get(key: String): T = getMember(key).cast()

    /** Allows you to read JS properties of the given [Value] using Kotlin indexing syntax. */
    operator fun Value.get(key: String): Value = getMember(key)

    /** Allows you to set JS properties of the given [Value] using Kotlin indexing syntax. */
    operator fun Value.set(key: String, value: Any?) = putMember(key, value)

    private val bindings = NodeJS.polyglotContext().polyglotBindings

    /** Implementation for [bind]. The necessary operator functions are defined as extensions to allow for reified generics. */
    class Binding<T>

    operator fun <T> Binding<T>.setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        // This rather ugly hack is required as we can't just insert the name directly,
        // we have to go via an intermediate 'bindings' map.
        bindings["__nodejvm_transfer"] = value
        NodeJS.eval("${property.name} = Polyglot.import('__nodejvm_transfer');")
        bindings.removeMember("__nodejvm_transfer")
    }

    inline operator fun <reified T> Binding<T>.getValue(thisRef: Any?, property: KProperty<*>): T = eval(property.name)

    inner class Binder<T>(private val default: T? = null) {
        operator fun provideDelegate(thisRef: Any?, prop: KProperty<*>): Binding<T> {
            val b = Binding<T>()
            if (default != null)
                b.setValue(null, prop, default)
            return b
        }
    }

    /**
     * Use this in property delegate syntax to access top level global variables in the NodeJS context. By declaring
     * a variable as `var x: String by bind()` you can read and write the 'x' global variable in JavaScript world.
     */
    fun <T> bind(default: T? = null) = Binder(default)
}

/** Wraps JS objects with some Bean property convenience glue. */
private class JSTranslationProxyHandler(private val value: Value) : InvocationHandler {
    companion object {
        fun isTranslateableInterface(c: Class<*>) =
            c.isInterface && !c.isAnnotationPresent(FunctionalInterface::class.java)
    }

    // This code does a lot of redundant work on every method call and could be optimised with caches.
    init {
        check(nodejs { value.hasMembers() }) { "Cannot translate this value to an interface because it has no members." }
    }

    override fun invoke(proxy: Any, method: Method, args: Array<out Any>?): Any? {
        // Apply Bean-style naming pattern matching.
        val name = method.name
        fun hasPropName(p: Int) = name.length > p && name[p].isUpperCase()
        val getter = name.startsWith("get") && hasPropName(3)
        val setter = name.startsWith("set") && hasPropName(3)
        val izzer = name.startsWith("is") && hasPropName(2)
        val isPropAccess = getter || setter || izzer
        val propName = if (isPropAccess) {
            if (getter || setter) {
                name.drop(3).decapitalize()
            } else {
                check(izzer)
                name.drop(2).decapitalize()
            }
        } else null

        val returnType = method.returnType
        val parameterCount = method.parameterCount

        when {
            izzer -> check(returnType == Boolean::class.java && parameterCount == 0) {
                "Methods starting with 'is' should return boolean and have no parameters."
            }
            getter -> check(parameterCount == 0) { "Methods starting with 'get' should not have any parameters." }
            setter -> check(parameterCount == 1) { "Methods starting with 'set' should have a single parameter." }
        }

        NodeJS.checkOnMainNodeThread()

        return if (propName != null) {
            if (getter || izzer) {
                val member = value.getMember(propName)
                    ?: throw IllegalStateException("No property with name $propName found: [${value.memberKeys}] ")
                member.`as`(returnType)
            } else {
                check(setter)
                value.putMember(propName, args!!.single())
                null
            }
        } else {
            // Otherwise treat it as a method call.
            check(value.canInvokeMember(name)) { "Method $name does not appear to map to an executable member: [${value.memberKeys}]" }
            val result = value.invokeMember(name, *(args ?: emptyArray<Any>()))
            // The result should be thrown out if expecting void, or translated again if the return type is a
            // non-functional interface (functional interfaces are auto-translated by Polyglot already), or
            // otherwise we just rely on the default Polyglot handling which is pretty good most of the time.
            when {
                returnType == Void.TYPE -> null
                isTranslateableInterface(returnType) -> Proxy.newProxyInstance(
                    this.javaClass.classLoader,
                    returnType.interfaces,
                    JSTranslationProxyHandler(result)
                )
                else -> result.`as`(returnType)
            }
        }
    }
}

