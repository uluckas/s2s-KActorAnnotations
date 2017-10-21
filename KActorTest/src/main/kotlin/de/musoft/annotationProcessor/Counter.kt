package de.musoft.annotationProcessor.test

import de.musoft.annotationProcessor.KActorAnnotation

@KActorAnnotation
internal class Counter {
    var i = 0
    //private var counterActor = CounterActor()
    fun inc() { i++ }
}