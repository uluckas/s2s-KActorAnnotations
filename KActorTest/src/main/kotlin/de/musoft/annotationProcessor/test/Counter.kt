package de.musoft.annotationProcessor.test

import de.musoft.annotationProcessor.KActor
import kotlinx.coroutines.experimental.CompletableDeferred

@KActor
open class Counter(private val dummy: java.lang.String) {
    private var i: Integer = Integer(0)

    open fun getI(result: CompletableDeferred<Integer>) {
        result.complete(i)
    }

    open fun setI(result: CompletableDeferred<Unit>, value: Integer) {
        i = value
        result.complete(Unit)
    }

    open fun inc(result: CompletableDeferred<Unit>) {
        i = Integer(i.toInt() + 1)
        result.complete(Unit)
    }
}