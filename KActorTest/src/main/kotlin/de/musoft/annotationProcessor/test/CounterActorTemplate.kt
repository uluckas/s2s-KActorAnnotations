package de.musoft.annotationProcessor.test

import kotlinx.coroutines.experimental.CompletableDeferred
import kotlinx.coroutines.experimental.CoroutineDispatcher
import kotlinx.coroutines.experimental.DefaultDispatcher
import kotlinx.coroutines.experimental.channels.actor

class CounterActorTemplate(private val context: CoroutineDispatcher = DefaultDispatcher) {

    private sealed class CounterMsg {

        class GetI(val response: CompletableDeferred<Int>?) : CounterMsg()
        class SetI(val response: CompletableDeferred<Unit>?, val p0: Int) : CounterMsg()
        class Inc(val response: CompletableDeferred<Unit>?) : CounterMsg()

    }

    private var delegate = Counter()

    private var actor = actor<CounterMsg>(context) {
        for (msg in channel) {
            when (msg) {
                is CounterMsg.GetI -> {
                    try {
                        val i = delegate.getI()
                        msg.response?.complete(i)
                    } catch (t: Throwable) {
                        msg.response?.completeExceptionally(t)
                    }
                }
                is CounterMsg.SetI -> {
                    try {
                        val i = delegate.setI(msg.p0)
                        msg.response?.complete(i)
                    } catch (t: Throwable) {
                        msg.response?.completeExceptionally(t)
                    }
                }
                is CounterMsg.Inc -> {
                    try {
                        val i = delegate.inc()
                        msg.response?.complete(i)
                    } catch (t: Throwable) {
                        msg.response?.completeExceptionally(t)
                    }
                }
            }
        }
    }

    suspend fun getIDeferred(): CompletableDeferred<Int> {
        val response = CompletableDeferred<Int>()
        actor.send(CounterMsg.GetI(response))
        return response
    }

    suspend fun getIAsync(): Unit {
        actor.send(CounterMsg.GetI(null))
    }

    suspend fun getI(): Int = getIDeferred().await()

    suspend fun setIDeferred(value: Int): CompletableDeferred<Unit> {
        val response = CompletableDeferred<Unit>()
        actor.send(CounterMsg.SetI(response, value))
        return response
    }

    suspend fun setIAsync(value: Int): Unit {
        actor.send(CounterMsg.SetI(null, value))
    }

    suspend fun setI(value: Int): Unit = setIDeferred(value).await()

    suspend fun incDefered(): CompletableDeferred<Unit> {
        val response = CompletableDeferred<Unit>()
        actor.send(CounterMsg.Inc(response))
        return response
    }

    suspend fun incAsync(): Unit {
        actor.send(CounterMsg.Inc(null))
    }

    suspend fun inc(): Unit = incDefered().await()

}