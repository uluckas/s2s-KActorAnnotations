package de.musoft.annotationProcessor.test.primeSieve

import de.musoft.annotationProcessor.KActor

@KActor
abstract class DivisorFilter(private val divisor: Integer,
                             private val generator: NumberGenerator) : NumberGenerator, NumberProcessor {

    private var nextStage: NumberProcessor? = null

    override fun generateNext(numberProcessor: NumberProcessor) {
        nextStage = numberProcessor
        generator.generateNext(this)
    }

    override fun process(i: Integer) {
        if (i.toInt() % divisor.toInt() != 0) {
            nextStage?.process(i)
        } else {
            generator.generateNext(this)
        }
    }
}
