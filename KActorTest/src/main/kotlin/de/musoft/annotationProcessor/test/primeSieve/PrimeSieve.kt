package de.musoft.annotationProcessor.test.primeSieve

import de.musoft.annotationProcessor.KActor

@KActor
abstract class PrimeSieve : NumberProcessor {

    private var filterChain: NumberGenerator = SequentialNumberGeneratorActor(Integer(2))
    private var count = 0

    open fun generate(n: Integer) {
        this.count = n.toInt()
        filterChain.generateNext(this)
    }

    override fun process(i: Integer) {
        println("$i")
        count--
        if (count > 0) {
            filterChain = DivisorFilterActor(i, filterChain)
            filterChain.generateNext(this)
        }
    }
}
