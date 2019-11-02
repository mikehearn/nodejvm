package net.plan99.nodejs.sample.spinners

import net.plan99.nodejs.kotlin.nodejs
import org.graalvm.polyglot.Value

fun main() {
    val spinner: Value = nodejs {
        val ora: Value = eval("require('ora');")
        val spinner = ora.execute("Hello world")
        spinner["start"].executeVoid()
        spinner
    }
    Thread.sleep(5000)
    nodejs {
        spinner["text"] = "Working"
        spinner["info"].execute("Info!")
    }
    Thread.sleep(2000)
}