package com.github.recognized.screencast.recorder.sound

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.util.ProgressWindow
import com.intellij.util.containers.ContainerUtil
import javax.sound.sampled.*
import kotlin.concurrent.thread
import kotlin.system.measureTimeMillis


object SoundRecorder {
  private val LOG = logger<SoundRecorder>()
  private val LISTENERS = ContainerUtil.newConcurrentSet<StateListener>()
  private var STATE: InternalState =
    InternalState.Idle
    set(newValue) {
      val oldValue = field
      field = newValue
      fireStateChanged(oldValue.toState(), newValue.toState())
    }

  val RECORD_FORMAT = AudioFormat(
    AudioFormat.Encoding.PCM_SIGNED,
    44100f,
    16,
    1,
    2,
    44100f,
    false
  )

  init {
    // logger listener
    addListener(object :
      StateListener {
      override fun stateChanged(
        oldValue: State,
        newValue: State
      ) {
        LOG.info("Changed state: $oldValue -> $newValue")
      }
    })
  }

  fun getState(): State {
    ApplicationManager.getApplication().assertIsDispatchThread()
    return STATE.toState()
  }

  /**
   * @throws javax.sound.sampled.LineUnavailableException If data line cannot be acquired, or opened,
   * or if recording format is not supported.
   */
  fun start(lineProcessor: (TargetDataLine) -> Unit) {
    ApplicationManager.getApplication().assertIsDispatchThread()
    val state = STATE
    when (state) {
      is InternalState.Paused -> {
        // Start after pause
        state.line.start()
        STATE =
            InternalState.Recording(state.line)
        return
      }
      is InternalState.Recording, is InternalState.Preparing -> return
    }
    val info = DataLine.Info(TargetDataLine::class.java, RECORD_FORMAT)
    if (!AudioSystem.isLineSupported(info)) {
      throw LineUnavailableException("Audio format ($RECORD_FORMAT) is not supported")
    }
    val startTime = System.currentTimeMillis()
    val line = AudioSystem.getLine(info) as TargetDataLine
    STATE =
        InternalState.Preparing
    val p = ProgressWindow(false, false, null)
    p.title = "Screencast Recorder"
    p.setDelayInMillis(80)
    p.start()
    p.text = "Preparing audio input device..."
    thread(start = true) {
      line.use {
        line.open(RECORD_FORMAT)
        line.start()
        LOG.info("Took ${System.currentTimeMillis() - startTime}ms to start")
        ApplicationManager.getApplication().invokeAndWait {
          STATE =
              InternalState.Recording(line)
        }
        p.stop()
        p.processFinish()
        try {
          lineProcessor(line)
        } finally {
          ApplicationManager.getApplication().invokeAndWait {
            STATE =
                InternalState.Idle
          }
        }
      }
    }
  }

  fun pause() {
    ApplicationManager.getApplication().assertIsDispatchThread()
    val state = STATE
    when (state) {
      is InternalState.Idle, is InternalState.Paused, is InternalState.Preparing -> return
      is InternalState.Recording -> {
        val time = measureTimeMillis {
          state.line.stop()
        }
        STATE =
            InternalState.Paused(state.line)
        LOG.info("Took ${time}ms to pause")
      }
    }
  }

  fun stop() {
    ApplicationManager.getApplication().assertIsDispatchThread()
    val state = STATE
    when (state) {
      is InternalState.Idle, is InternalState.Preparing -> return
      is InternalState.Recording, is InternalState.Paused -> {
        val time = measureTimeMillis {
          if (state is InternalState.Recording) {
            state.line.stop()
            state.line.flush()
            state.line.close()
          } else {
            (state as InternalState.Paused).line.close()
          }
        }
        STATE =
            InternalState.Idle
        LOG.info("Took ${time}ms to pause")
      }
    }
  }

  fun addListener(listener: StateListener) {
    LISTENERS.add(listener)
  }

  fun removeListener(listener: StateListener) {
    LISTENERS.remove(listener)
  }

  private fun fireStateChanged(oldValue: State, newValue: State) {
    ApplicationManager.getApplication().assertIsDispatchThread()
    if (oldValue == newValue) return
    for (listener in LISTENERS) {
      listener.stateChanged(oldValue, newValue)
    }
  }

  private sealed class InternalState {
    object Idle : InternalState()
    object Preparing : InternalState()
    class Recording(val line: TargetDataLine) : InternalState()
    class Paused(val line: TargetDataLine) : InternalState()
  }

  private fun InternalState.toState() = when (this) {
    is InternalState.Idle -> State.IDLE
    is InternalState.Preparing -> State.IDLE
    is InternalState.Paused -> State.PAUSED
    is InternalState.Recording -> State.RECORDING
  }

  interface StateListener {
    fun stateChanged(oldValue: State, newValue: State)
  }

  enum class State {
    IDLE, RECORDING, PAUSED
  }
}