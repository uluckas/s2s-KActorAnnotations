package de.musoft.annotationProcessor.test

import de.musoft.annotationProcessor.KActorAnnotation

@KActorAnnotation
class Counter {
    private var i = 0
    private var counterActor = CounterActor()
    fun getI() = i

    fun setI(value: Int) {
        i = value
    }

    fun inc() {
        //synchronized(this) {
        i++
        //}
    }
}