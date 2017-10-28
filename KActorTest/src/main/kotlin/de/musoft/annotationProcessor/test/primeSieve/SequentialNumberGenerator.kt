package de.musoft.annotationProcessor.test.primeSieve

import de.musoft.annotationProcessor.KActor

@KActor
abstract class SequentialNumberGenerator(private var i: Integer = Integer(0)) : NumberGenerator {

    override fun generateNext(numberProcessor: NumberProcessor) {
        numberProcessor.process(i)
        i = Integer(i.toInt() + 1)
    }

}
