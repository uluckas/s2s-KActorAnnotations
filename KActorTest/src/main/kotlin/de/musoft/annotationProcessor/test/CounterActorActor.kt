package de.musoft.annotationProcessor.test

import kotlinx.coroutines.experimental.CompletableDeferred
import kotlinx.coroutines.experimental.CoroutineDispatcher
import kotlinx.coroutines.experimental.channels.ActorJob
import kotlinx.coroutines.experimental.channels.actor

class CounterActorActor : Counter {

    private fun actorFactory(context: CoroutineDispatcher): ActorJob<CounterMsg> = actor(context) {
        println("Actor up and running")
        for (msg in channel) {
            when (msg) {
                is CounterMsg.GetI -> {
                    try {
                        super.getI(msg.result)
                    } catch (t: Throwable) {
                        val currentThread = Thread.currentThread()
                        currentThread.uncaughtExceptionHandler.uncaughtException(currentThread, t)
                    }
                }
                is CounterMsg.SetI -> {
                    try {
                        super.setI(msg.result, msg.value)
                    } catch (t: Throwable) {
                        val currentThread = Thread.currentThread()
                        currentThread.uncaughtExceptionHandler.uncaughtException(currentThread, t)
                    }
                }
                is CounterMsg.Inc -> {
                    try {
                        super.inc(msg.result)
                    } catch (t: Throwable) {
                        val currentThread = Thread.currentThread()
                        currentThread.uncaughtExceptionHandler.uncaughtException(currentThread, t)
                    }
                }
            }
        }
    }

    private val actor: ActorJob<CounterMsg>

    constructor(i: Integer, context: CoroutineDispatcher = kotlinx.coroutines.experimental.DefaultDispatcher) : super(i) {
        this.actor = actorFactory(context)
    }


    override fun getI(result: CompletableDeferred<Integer>): Unit {
        actor.offer(CounterMsg.GetI(result))
    }

    override fun setI(result: CompletableDeferred<Unit>, value: Integer): Unit {
        actor.offer(CounterMsg.SetI(result, value))
    }

    override fun inc(result: CompletableDeferred<Unit>): Unit {
        actor.offer(CounterMsg.Inc(result))
    }

    private sealed class CounterMsg {
        class GetI(val result: CompletableDeferred<Integer>) : CounterMsg()

        class SetI(val result: CompletableDeferred<Unit>, val value: Integer) : CounterMsg()

        class Inc(val result: CompletableDeferred<Unit>) : CounterMsg()
    }
}

