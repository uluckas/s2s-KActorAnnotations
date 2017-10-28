package de.musoft.annotationProcessor.test

import kotlinx.coroutines.experimental.*
import kotlin.system.measureTimeMillis

private val concurrent = 6
private val operations = 1000

fun main(args: Array<String>) {
    val launcherDispatcher = newFixedThreadPoolContext(concurrent, "Launcher Context")
    val actorDispatcher = Unconfined
    //val actorDispatcher = CommonPool
    val actor = CounterActor(Integer(0), actorDispatcher)
    runBlocking(Unconfined) {
        val time = measureTimeMillis {
            val start = System.currentTimeMillis()
            println("${System.currentTimeMillis() - start}: Launching jobs")
            val jobs = List(size = concurrent) { i ->
                launch(launcherDispatcher) {
                    val results = List(operations) { j ->
                        val result = CompletableDeferred<Unit>()
                        actor.inc(result)
                        result
                    }
                    results.forEach { it.await() }
                }
            }
            println("${System.currentTimeMillis() - start}: Waiting for all jobs to be done")
            var i = 0
            jobs.forEach {
                it.join()
            }
            println("${System.currentTimeMillis() - start}: All jobs done")
            val result = CompletableDeferred<Integer>()
            actor.getI(result)
            println("${System.currentTimeMillis() - start}: Final value: ${result.await()}")
        }
        println("Took $time ms")
    }
}

