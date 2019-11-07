/**
 * Demo of using the 'ora' module, which generates pretty terminal spinners. Translated from
 * this JavaScript code sample:
 *
 * ```
 * const ora = require('ora');
 *
 * const spinner = ora('Loading unicorns').start();
 *
 * setTimeout(() => {
 *     spinner.color = 'yellow';
 *     spinner.text = 'Loading rainbows';
 * }, 1000);
 * ```
 *
 */
package net.plan99.nodejs.sample.spinners

import net.plan99.nodejs.kotlin.nodejs

interface Ora {
    fun start(text: String)
    fun info(text: String)
    fun warn(text: String)
    fun error(text: String)

    var text: String
    var prefixText: String?
    var color: String
}

fun main() {
    val spinner: Ora = nodejs { eval("require('ora')()") }

    nodejs {
        spinner.start("Loading unicorns ...")
    }

    // We don't want to block the nodeJS thread by sleeping inside a nodejs{} block.
    // This sleep represents some sort of "work".
    Thread.sleep(2000)

    // Change color and message.
    nodejs {
        spinner.color = "red"
        spinner.text = "Loading rainbows"
        spinner.prefixText = "Working"
    }

    // "Work" a bit more.
    Thread.sleep(3000)

    // Show a nice "info" message instead.
    nodejs {
        spinner.prefixText = null
        spinner.info("Info!")
    }
}