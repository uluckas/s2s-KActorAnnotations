package de.musoft.annotationProcessor.test

import kotlinx.coroutines.experimental.CompletableDeferred
import kotlinx.coroutines.experimental.CoroutineDispatcher
import kotlinx.coroutines.experimental.DefaultDispatcher
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.sync.Mutex

class CounterActorSynchronized constructor(val context: CoroutineDispatcher = DefaultDispatcher) : Counter(java.lang.String("")) {

    private val messageProcessingMonitor = Mutex()

    override fun getI(result: CompletableDeferred<Integer>): Unit {
        launch {
            messageProcessingMonitor.lock()
            super.getI(result)
            messageProcessingMonitor.unlock()
        }
    }

    override fun setI(result: CompletableDeferred<Unit>, value: Integer): Unit {
        launch {
            messageProcessingMonitor.lock()
            super.setI(result, value)
            messageProcessingMonitor.unlock()
        }
    }

    override fun inc(result: CompletableDeferred<Unit>): Unit {
        launch {
            messageProcessingMonitor.lock()
            super.inc(result)
            messageProcessingMonitor.unlock()
        }
    }

}
