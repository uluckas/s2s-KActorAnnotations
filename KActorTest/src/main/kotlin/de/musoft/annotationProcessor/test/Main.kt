package de.musoft.annotationProcessor.test

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
                    actor.incAndForget()
                }
            }
        }
        jobs.forEach { job -> job.join() }
        println("Final value: ${actor.getI().await()}")
    }
}