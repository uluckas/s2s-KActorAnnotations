package de.musoft.utils

class ParalelIterator<T1, T2>(private var iterator1: Iterator<T1>, private var iterator2: Iterator<T2>) : Iterator<Pair<T1, T2>> {

    constructor(collection1: Collection<T1>, collection2: Collection<T2>) : this(collection1.iterator(), collection2.iterator())

    override fun next(): Pair<T1, T2> = Pair(iterator1.next(), iterator2.next())

    override fun hasNext(): Boolean = iterator1.hasNext() && iterator2.hasNext()

}
