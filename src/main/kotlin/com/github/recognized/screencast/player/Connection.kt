package com.github.recognized.screencast.player

import java.io.PipedInputStream
import java.net.InetAddress
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread
import kotlin.math.min

const val PORT = 26000

class PlayerClient(val socket: Socket) : AutoCloseable by socket {

  private var playTime = 0L
  private var startTime = 0L
  private val st = socket.getInputStream()
  private val input = st.bufferedReader(Charsets.UTF_8)
  private val output = socket.getOutputStream().writer(Charsets.UTF_8)

  @Volatile
  private var state: PlayerServer.State = PlayerServer.State.IDLE
    set(value) {
      field = value
      report(PlayerServer.Response.STATE, value.name)
    }

  private var waitLeft = AtomicLong(0)

  init {
    thread {
      try {
        while (st.available() == 0 && !socket.isInputShutdown) {
          Thread.sleep(100)
        }
        for (line in input.lineSequence()) {
          PlayerServer.State.values().firstOrNull { line.trim() == it.name }?.let {
            state = it
          } ?: PlayerServer.Requests.values().firstOrNull {
            line.trim().startsWith(it.name)
          }?.let {
            when (it) {
              PlayerServer.Requests.DELAY -> {
                val delay = line.substringAfter(it.name).trim().toLong()
                waitLeft.addAndGet(delay)
              }
            }
          } ?: error("Unknown request: $line")
          while (st.available() == 0 && !socket.isInputShutdown) {
            Thread.sleep(100)
          }
        }
      } catch (ex: SocketException) {
      }
    }
  }

  private fun time() {
    report(PlayerServer.Response.TIME, (System.currentTimeMillis() - startTime + playTime).toString())
  }

  private fun report(type: PlayerServer.Response, str: String) {
    output.write("${type.name} ${str.replace('\n', ' ')}\n")
    output.flush()
  }

  fun timeOffset(ms: Long) {
    time()
    handleState()
    var wait = waitLeft.addAndGet(ms)
    while (wait > 0L) {
      Thread.sleep(min(100, wait))
      wait = waitLeft.addAndGet(-min(100, wait))
      time()
      handleState()
    }
    time()
  }

  private fun log(message: String) {
    report(PlayerServer.Response.LOG, message.replace('\n', ' '))
  }

  fun ___end() {
    state = PlayerServer.State.END
    time()
  }

  fun ___start() {
    while (state != PlayerServer.State.PLAY) {
      Thread.sleep(150)
    }
    playTime = 0L
    waitLeft.set(0)
    startTime = System.currentTimeMillis()
    time()
  }

  fun ___codeError(ex: Throwable) {
    report(PlayerServer.Response.CODE_ERROR, ex.message ?: ex::class.simpleName ?: "")
    log(ex.toString())
  }

  private fun handleState() {
    when (state) {
      PlayerServer.State.PAUSE -> {
        playTime += System.currentTimeMillis() - startTime
        while (true) {
          Thread.sleep(100)
          if (state != PlayerServer.State.PAUSE) {
            startTime = System.currentTimeMillis()
            break
          }
        }
      }
      PlayerServer.State.IDLE -> {
        throw StopClient()
      }
      PlayerServer.State.STOP -> {
        socket.shutdownInput()
        socket.shutdownOutput()
        throw CloseClient()
      }
      else -> Unit
    }
  }
}

class StopClient : Exception()
class CloseClient : Exception()

fun ___connectClient(): PlayerClient {
  val tries = 30
  var tried = 0
  while (true) {
    try {
      val socket = Socket(InetAddress.getByName("localhost"), PORT)
      return PlayerClient(socket)
    } catch (ex: Throwable) {
      println("Trying to connect...")
      if (tried-- > tries) {
        throw IllegalStateException("Cannot connect. Timeout: ${tried}s. Port: $PORT")
      }
      Thread.sleep(1000)
    }
  }
}

class PlayerServer(val socket: Socket) : AutoCloseable by socket {

  @Volatile
  var stateChanged: (old: State, newState: State) -> Unit = { _, _ -> }
  @Volatile
  var codeError: (String) -> Unit = {}
  @Volatile
  var timeHandler: (Long) -> Unit = {}
  @Volatile
  var logger: (String) -> Unit = {}

  @Volatile
  var currentState: State = State.IDLE
    set(value) {
      stateChanged(field, value)
      field = value
    }

  private val input = socket.getInputStream().bufferedReader(Charsets.UTF_8)
  private val output = socket.getOutputStream().bufferedWriter(Charsets.UTF_8)

  init {
    if (socket.isClosed || !socket.isConnected || socket.isInputShutdown || socket.isOutputShutdown) {
      throw error("Socket closed")
    }
    thread {
      try {
        for (line in input.lineSequence()) {
          Response.values().firstOrNull { line.startsWith(it.name) }?.let {
            val msg = line.removePrefix(it.name).trim()
            when (it) {
              Response.LOG -> logger(msg)
              Response.CODE_ERROR -> codeError(msg)
              Response.TIME -> timeHandler(msg.toLong())
              Response.STATE -> {
                State.values().first { state -> msg == state.name }.let { state ->
                  currentState = state
                }
              }
            }
          }
        }
      } catch (socketEx: SocketException) {

      } catch (ex: Throwable) {
        logger(ex.message ?: ex::class.simpleName ?: "")
      }
    }
  }


  fun pause() {
    sendCommand(State.PAUSE)
  }

  fun reset() {
    sendCommand(State.IDLE)
  }

  fun stop() {
    sendCommand(State.STOP)
  }

  fun play() {
    sendCommand(State.PLAY)
  }

  fun correctTime(delay: Long) {
    sendRequest(Requests.DELAY, delay.toString())
  }

  private fun sendRequest(request: Requests, value: String) {
    output.write("${request.name} $value\n")
    output.flush()
  }

  private fun sendCommand(command: State) {
    output.write(command.name)
    output.write("\n")
    output.flush()
  }

  enum class Requests {
    DELAY
  }

  enum class State {
    STOP, PAUSE, PLAY, END, IDLE
  }

  enum class Response {
    CODE_ERROR, LOG, TIME, STATE
  }
}