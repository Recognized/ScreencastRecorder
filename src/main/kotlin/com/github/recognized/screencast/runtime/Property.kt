package com.github.recognized.screencast.runtime

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class Property<T>(value: T) {
    private val registrar = mutableSetOf<(T) -> Unit>()
    
    var value: T = value
        set(value) {
            val fire = field != value
            field = value
            if (fire) {
                registrar.forEach {
                    it(value)
                }
            }
        }
    
    
    fun forEach(disposable: Disposable, fn: (T) -> Unit) {
        registrar.add(fn)
        Disposer.register(disposable, Disposable {
            registrar.remove(fn)
        })
    }
}

suspend fun <T> Property<T>.await(disposable: Disposable, value: T) {
    awaitValue(disposable) {
        it == value
    }
}

suspend fun <T> Property<T>.awaitValue(disposable: Disposable, predicate: (T) -> Boolean) {
    suspendCancellableCoroutine<Unit>{ cont ->
        Disposer.register(disposable, Disposable {
            cont.resumeWithException(CancellationException())
        })
        forEach(disposable) {
            if (predicate(it) && !Disposer.isDisposed(disposable)) {
                cont.resume(Unit)
            }
        }
    }
}