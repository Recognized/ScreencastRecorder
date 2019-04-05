package com.github.recognized.screencast.player

val inlineLib = """

val PORT = 26000

class PlayerClient(val socket: Socket) : AutoCloseable by socket {

  private var playTime = 0L
  private val input = socket.getInputStream().bufferedReader(Charsets.UTF_8)
  private val output = socket.getOutputStream().bufferedWriter(Charsets.UTF_8)

  @Volatile
  private var state: String = ""
    set(value) {
      field = value
      report(value)
      time()
    }

  private var waitLeft = 0L

  init {
    thread {
      for (line in input.lineSequence()) {
        if (line in listOf(PlayerServer.PLAY, PlayerServer.STOP, PlayerServer.PAUSE)) {
          state = line
        } else {
          log("Unknown command '" + line + "'")
        }
      }
    }
  }

  private fun time() {
    report("TIME " + playTime)
  }

  private fun report(str: String) {
    output.write(str + "\n")
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
    report("LOG " + message.replace('\n', ' '))
  }

  fun ___end() {
    state = PlayerServer.END
    time()
  }

  fun ___start() {
    while (state != PlayerServer.PLAY) {
    }
    playTime = 0
    time()
  }

  private fun loop() {
    var previousTime = System.currentTimeMillis()
    while (waitLeft > 0L) {
      if (state != PlayerServer.PLAY) {
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
      PlayerServer.PAUSE -> {
        while (true) {
          Thread.sleep(16)
          if (state != PlayerServer.PAUSE) {
            break
          }
        }
      }
      PlayerServer.STOP -> {
        throw Exception("STOP")
      }
    }
  }
}

fun ___connectClient(): PlayerClient {
  while(true)  {
    try {
      val socket = Socket(InetAddress.getByName("localhost"), PORT)
      return PlayerClient(socket)
    } catch (ex: Throwable) {
      println()
      Thread.sleep(1000)
    }
  }
}

class PlayerServer(val socket: Socket, val log: (String) -> Unit) : AutoCloseable by socket {

  @Volatile
  var eventHandler: (Int) -> Unit = {}
  @Volatile
  var timeHandler: (Long) -> Unit = {}
  private val input = socket.getInputStream().bufferedReader(Charsets.UTF_8)
  private val output = socket.getOutputStream().bufferedWriter(Charsets.UTF_8)

  init {
    if (socket.isClosed || !socket.isConnected || socket.isInputShutdown || socket.isOutputShutdown) {
      throw error("Socket closed")
    }
    thread {
      for (line in input.lineSequence()) {
        val code = when (line) {
          STOP -> STOP_CODE
          PAUSE -> PAUSE_CODE
          PLAY -> PLAY_CODE
          else -> {
            log(line.removePrefix("LOG "))
            -1
          }
        }
        if (line.startsWith("TIME")) {
          timeHandler(line.removePrefix("TIME").trim().toLong())
        } else {
          eventHandler(code)
        }
      }
    }
  }


  fun pause() {
    sendCommand(PAUSE)
  }

  fun stop() {
    sendCommand(STOP)
  }

  fun play() {
    sendCommand(PLAY)
  }

  private fun sendCommand(command: String) {
    output.write(command + "\n")
    output.flush()
  }

  companion object {
    const val PAUSE = "pause"
    const val STOP = "stop"
    const val PLAY = "play"
    const val END = "end"

    const val STOP_CODE = 0
    const val PAUSE_CODE = 1
    const val PLAY_CODE = 2
    const val END_CODE = 3
  }
}

fun ___error(ex: Throwable) {
  println("CLIENT: " + ex)
}
"""

val imports = """
  import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.concurrent.thread
"""