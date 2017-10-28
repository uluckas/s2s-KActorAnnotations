package de.musoft.annotationProcessor.test

import kotlinx.coroutines.experimental.CompletableDeferred
import kotlinx.coroutines.experimental.CoroutineDispatcher
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.sync.Mutex

class CounterActorSynchronized : Counter {

    private val context: CoroutineDispatcher

    constructor(i: Integer, context: CoroutineDispatcher = kotlinx.coroutines.experimental.DefaultDispatcher) : super(i) {
        this.context = context
    }

    private val messageProcessingMonitor = Mutex()

    override fun getI(result: CompletableDeferred<Integer>): Unit {
        launch(context) {
            messageProcessingMonitor.lock()
            super.getI(result)
            messageProcessingMonitor.unlock()
        }
    }

    override fun setI(result: CompletableDeferred<Unit>, value: Integer): Unit {
        launch(context) {
            messageProcessingMonitor.lock()
            super.setI(result, value)
            messageProcessingMonitor.unlock()
        }
    }

    override fun inc(result: CompletableDeferred<Unit>): Unit {
        launch(context) {
            messageProcessingMonitor.lock()
            super.inc(result)
            messageProcessingMonitor.unlock()
        }
    }

}
