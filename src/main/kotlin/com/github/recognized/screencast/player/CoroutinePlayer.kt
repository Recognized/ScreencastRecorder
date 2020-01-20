package com.github.recognized.screencast.player

import com.github.recognized.screencast.runtime.Property
import com.github.recognized.screencast.runtime.awaitValue
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.testGuiFramework.impl.GuiTestCase
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.math.max
import kotlin.math.min

class CoroutinePlayer(
        private val parent: Disposable,
        private val scenario: Scenario,
        private val errorHandler: (ex: Throwable) -> Unit = {}
) : GuiTestCase() {
    private var lastOffsetTime = 0L
    private var internalSignal: PlaySignal? = null
    val signal = Property<PlaySignal?>(null)
    val internalState = Property<InternalState>(Idle)
    
    init {
        signal.forEach(parent) { signal ->
            when (signal) {
                PlaySignal.PLAY -> {
                    when (val s = internalState.value) {
                        is Idle -> {
                            parent.launch(Dispatchers.Main) {
                                try {
                                    scenario.play(this@CoroutinePlayer)
                                    internalState.value = Stop(null)
                                } catch (ex: Throwable) {
                                    errorHandler(ex)
                                    internalState.value = Stop(ex)
                                }
                            }
                        }
                        is Pause -> {
                            internalState.value = Play(System.currentTimeMillis(), s.spentTime)
                        }
                    }
                }
                else -> {
                    internalSignal = signal
                }
            }
        }
    }
    
    suspend fun timeOffset(ms: Long) {
        lastOffsetTime += ms
        handleSignal()
        while (executionTime() < lastOffsetTime) {
            val delta = max(min(16, lastOffsetTime - executionTime()), 0L)
            delay(delta)
            handleSignal()
        }
    }
    
    private fun executionTime(): Long {
        return when (val state = internalState.value) {
            is Idle, is Stop -> 0L
            is Play -> System.currentTimeMillis() - state.startTime + state.spentTime
            is Pause -> state.spentTime
        }
    }
    
    private suspend fun handleSignal() {
        val s = internalSignal
        internalSignal = null
        when (s) {
            PlaySignal.PAUSE -> {
                when (internalState.value) {
                    is Play -> {
                        internalState.value = Pause(executionTime())
                        internalState.awaitValue(parent) {
                            it !is Pause
                        }
                        handleSignal()
                    }
                }
            }
            PlaySignal.STOP -> {
                throw CancellationException("Player stopped")
            }
            else -> {
                // ignore
            }
        }
    }
}

fun Disposable.launch(context: CoroutineContext = EmptyCoroutineContext, fn: suspend CoroutineScope.() -> Unit) {
    val scope = CoroutineScope(Job())
    scope.launch(context = context, block = fn)
    Disposer.register(this, Disposable {
        scope.cancel("Disposed $this")
    })
}

enum class PlaySignal {
    STOP, PLAY, PAUSE
}

sealed class InternalState
object Idle : InternalState()
data class Pause(val spentTime: Long) : InternalState()
data class Play(val startTime: Long, val spentTime: Long) : InternalState()
data class Stop(val reason: Throwable?) : InternalState()

