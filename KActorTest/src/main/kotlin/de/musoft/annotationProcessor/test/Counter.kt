package de.musoft.annotationProcessor.test

import de.musoft.annotationProcessor.KActor
import kotlinx.coroutines.experimental.CompletableDeferred

@KActor
open class Counter {
    private var i = 0

    open fun getI(i: CompletableDeferred<Int>) {

    }

    open fun setI(value: Int) {
        i = value
    }

    open fun inc() {
        i++
    }
}