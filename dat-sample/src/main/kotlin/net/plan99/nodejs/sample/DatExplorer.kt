@file:Suppress("UNUSED_VARIABLE", "JSUnresolvedFunction", "JSUnresolvedVariable")
@file:JvmName("DatExplorer")
package net.plan99.nodejs.sample

import net.plan99.nodejs.kotlin.nodejs
import org.graalvm.polyglot.Value
import java.io.File
import java.util.function.Consumer

interface DatConnection {
    val host: String
    val port: Int
    val type: String
}

object ConnectionHandler : Consumer<Value> {
    override fun accept(v: Value) = nodejs {
        val conn = v.cast<DatConnection>()
        println("New connection to ${conn.host}:${conn.port} using ${conn.type}")
    }
}

fun main() {
    nodejs {
        val downloadPath by bind(File("download"))
        if (downloadPath.exists()) downloadPath.deleteRecursively()

        val callback by bind(ConnectionHandler)
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

    Thread.sleep(25000)
}