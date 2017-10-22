package de.musoft.annotationProcessor.test

import de.musoft.annotationProcessor.KActor

@KActor
class Counter {
    private var i = 0

    fun getI() = i

    fun setI(value: Int) {
        i = value
    }

    fun inc() {
        i++
    }
}