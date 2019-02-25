@file:Suppress("UNUSED_VARIABLE", "JSUnresolvedFunction", "JSUnresolvedVariable")
@file:JvmName("DatExplorer")
package net.plan99.nodejs.sample

import net.plan99.nodejs.kotlin.nodejs
import java.io.File
import java.util.function.Consumer

interface MemoryUsage {
    fun rss(): Long
    fun heapTotal(): Long
}

interface DatConnection {
    fun host(): String
    fun port(): Int
    fun type(): String
}

fun main() {
    nodejs {
        val downloadPath by bind(File("download"))
        if (downloadPath.exists()) downloadPath.deleteRecursively()

        val r: MemoryUsage = eval("process.memoryUsage()")
        println("rss is ${r.rss()}, heapTotal is ${r.heapTotal()}")

        val callback by bind(Consumer { m: Map<String, Any?> ->
            val map = m.asValue().cast<DatConnection>()
            val host: String = map.host()
            val port: Int = map.port()
            val type: String = map.type()
            println("""New connection to $host:$port using $type""")
        })

        run("""
            let Dat = require('dat-node');
            Dat(downloadPath.getName(), { key: "778f8d955175c92e4ced5e4f5563f69bfec0c86cc6f670352c457943666fe639" }, function(err, dat) {
                if (err) throw err;
                console.log("Joined DAT network!");
                let network = dat.joinNetwork();   // Downloads files automatically.
                network.on('connection', function(connection, info) {
                    callback(info)
                });
            });
         """)
    }

    Thread.sleep(Long.MAX_VALUE)
}