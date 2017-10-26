package de.musoft.annotationProcessor.test

import kotlinx.coroutines.experimental.CompletableDeferred
import kotlinx.coroutines.experimental.Unconfined
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import kotlin.system.measureTimeMillis

private val concurrent = 80
private val operations = 100000

fun main(args: Array<String>) {
    val dispatcher = Unconfined
    val actor = CounterActor(java.lang.String(""), dispatcher)
    runBlocking(dispatcher) {
        val time = measureTimeMillis {
            println("Launching jobs")
            val jobs = List(size = concurrent) { i ->
                println("$i")
                launch(dispatcher) {
                    repeat(operations) {
                        waitFor { result ->
                            actor.inc(result)
                        }
                    }
                }
            }
            println("Waiting for all jobs to be done")
            var i = concurrent
            jobs.forEach {
                it.join()
                println("${--i}")
            }
            println("Done")
            val result = CompletableDeferred<Integer>()
            actor.getI(result)
            println("Final value: ${result.await()}")
        }
        println("Took $time ms")
    }
}

private suspend inline fun <reified T> waitFor(block: (CompletableDeferred<T>) -> T): T {
    val result = CompletableDeferred<T>()
    block(result)
    return result.await()
}

