package de.musoft.annotationProcessor.test

import kotlinx.coroutines.experimental.CompletableDeferred
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking

private val concurrent = 80
private val operations = 100000

fun main(args: Array<String>) {
    val actor = CounterActor()

    runBlocking {
        val jobs = List(size = concurrent) {
            launch {
                repeat(operations) {
                    actor.inc()
                }
            }
        }
        jobs.forEach { job -> job.join() }
        val result = CompletableDeferred<Int>()
        actor.getI(result)
        println("Final value: ${result.await()}")
    }
}