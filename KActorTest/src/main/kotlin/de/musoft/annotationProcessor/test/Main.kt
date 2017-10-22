package de.musoft.annotationProcessor.test

import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import kotlin.system.measureTimeMillis

private val concurrent = 80
private val operations = 100000

private object Monitor : Any()

fun main(args: Array<String>) {
    val time = measureTimeMillis {
        val counter = CounterActorTemplate()
        runBlocking {
            val jobs = List(size = concurrent) {
                launch {
                    repeat(operations) {
                        counter.inc()
                    }
                }
            }
            jobs.forEach { job -> job.join() }
            println("Final value: ${counter.getI()}")
        }
    }
    println("Took $time ms")
}