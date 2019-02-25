var javaEntryPoint = process.argv[2];

// Set up a task queue that we'll proxy onto the NodeJS main thread.
//
// We have to do it this way because NodeJS/V8 are not thread safe,
// and JavaScript has no shared memory concurrency support, only
// message passing. It's like Visual Basic 6 all over again except
// this time without DCOM to wallpaper over what's really happening.
//
// Fortunately, we can call Java objects from JS and those CAN be
// shared memory. So we create an intermediate JS thread here that
// will spend its time blocked waiting for lambdas to be placed on
// the queue. Then it'll transmit them to the main event loop for
// execution.
let javaToJSQueue = new java.util.concurrent.LinkedBlockingDeque();
const {
    Worker, isMainThread, parentPort, workerData
} = require('worker_threads');

let worker = new Worker(`
    const { workerData, parentPort } = require('worker_threads');
    while (true) {
        parentPort.postMessage(workerData.take());
    }
`, { eval: true, workerData: javaToJSQueue });

worker.on('message', (callback) => {
    try {
        callback();
    } catch (e) {
        console.log(e);
    }
});

// We need this wrapper because GraalJS barfs if we try to call eval() directly from Java context, it assumes
// it will only ever be called from JS context.
let evalWrapper = function(str)  {
    return eval(str);
};

// Now pass control to the Java side.
Java.type('net.plan99.nodejs.NodeJS').boot(javaEntryPoint, javaToJSQueue, evalWrapper, process.argv.slice(2));