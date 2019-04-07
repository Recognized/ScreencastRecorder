package com.github.recognized.screencast.player

import java.net.InetAddress
import java.net.Socket
import java.net.SocketException
import kotlin.concurrent.thread

const val PORT = 26000

class PlayerClient(val socket: Socket) : AutoCloseable by socket {

  private var playTime = 0L
  private val input = socket.getInputStream().bufferedReader(Charsets.UTF_8)
  private val output = socket.getOutputStream().bufferedWriter(Charsets.UTF_8)

  @Volatile
  private var state: PlayerServer.State = PlayerServer.State.IDLE
    set(value) {
      field = value
      report(PlayerServer.Response.STATE, value.name)
      time()
    }

  private var waitLeft = 0L

  init {
    thread {
      try {
        for (line in input.lineSequence()) {
          PlayerServer.State.values().firstOrNull { line.trim() == it.name }?.let {
            state = it
          } ?: log("Unknown error $line")
        }
      } catch (ex: SocketException) {
      }
    }
  }

  private fun time() {
//    report(PlayerServer.Response.TIME, playTime.toString())
  }

  private fun report(type: PlayerServer.Response, str: String) {
    output.write("${type.name} ${str.replace('\n', ' ')}\n")
    output.flush()
  }

  fun timeOffset(ms: Long) {
    time()
    val start = playTime
    waitLeft = ms
    handleState()
    while (waitLeft > 0L) {
      loop()
      handleState()
    }
    playTime = Math.max(playTime, start + ms)
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
    }
    playTime = 0
    time()
  }

  fun ___codeError(ex: Throwable) {
    report(PlayerServer.Response.CODE_ERROR, ex.message ?: ex::class.simpleName ?: "")
    log(ex.toString())
  }

  private fun loop() {
    var previousTime = System.currentTimeMillis()
    while (waitLeft > 0L) {
      if (state != PlayerServer.State.PLAY) {
        break
      }
      Thread.sleep(16)
      val current = System.currentTimeMillis()
      waitLeft -= current - previousTime
      playTime += current - previousTime
      time()
      previousTime = current
    }
  }

  private fun handleState() {
    when (state) {
      PlayerServer.State.PAUSE -> {
        while (true) {
          Thread.sleep(16)
          if (state != PlayerServer.State.PAUSE) {
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

  private fun sendCommand(command: State) {
    output.write(command.name)
    output.write("\n")
    output.flush()
  }

  enum class State {
    STOP, PAUSE, PLAY, END, IDLE
  }

  enum class Response {
    CODE_ERROR, LOG, TIME, STATE
  }
}