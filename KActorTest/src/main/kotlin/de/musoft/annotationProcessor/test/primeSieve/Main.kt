package de.musoft.annotationProcessor.test.primeSieve

import kotlinx.coroutines.experimental.newFixedThreadPoolContext
import kotlinx.coroutines.experimental.runBlocking

fun main(args: Array<String>) {
    runBlocking(newFixedThreadPoolContext(5, "Pool")) {
        val primeSieve = PrimeSieveActor(context = coroutineContext)
        primeSieve.generate(Integer(10))
    }
}