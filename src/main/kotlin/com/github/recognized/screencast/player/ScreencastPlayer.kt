package com.github.recognized.screencast.player

import com.github.recognized.screencast.recorder.format.ScreencastZip
import com.github.recognized.screencast.recorder.format.ScreencastZipSettings
import com.github.recognized.screencast.recorder.sound.Player
import com.github.recognized.screencast.recorder.sound.impl.DefaultEditionsModel
import com.github.recognized.screencast.recorder.util.GridBagBuilder
import icons.ScreencastRecorderIcons
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.net.ServerSocket
import java.nio.file.Path
import javax.swing.JButton
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.concurrent.thread

class Logger(val name: String) {

  fun info(f: () -> Any) {
    println("[$name] " + f())
  }
}

fun showController(screencast: Path) {
  SwingUtilities.invokeLater {
    PlayerWindow(screencast)
  }
}

class PlayerWindow(screencast: Path) : JFrame(screencast.toString()) {

  private val player by lazy { ScreencastPlayerView(ScreencastZip(screencast)) }

  init {
    player.initComponent()
    add(player)
    size = Dimension(600, 200)
    player.size = size
    isVisible = true
    defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
    player.controller.init()
  }

  override fun dispose() {
    player.controller.use {
      super.dispose()
    }
  }
}

class ScreencastPlayerView(zip: ScreencastZip) : JPanel() {

  val controller = ScreencastPlayerController(zip)

  private val STOP_BUTTON by lazy {
    JButton(ScreencastRecorderIcons.STOP).apply {
      addActionListener {
        controller.stop()
      }
    }
  }

  private val PAUSE_BUTTON by lazy {
    JButton(ScreencastRecorderIcons.PAUSE).apply {
      addActionListener {
        controller.pause()
      }
    }
  }

  private val PLAY_BUTTON by lazy {
    JButton(ScreencastRecorderIcons.PLAY).apply {
      addActionListener {
        controller.play()
      }
    }
  }

  fun initComponent() {
    layout = GridBagLayout()
    add(STOP_BUTTON, GridBagBuilder().gridx(0).gridy(0).fill(GridBagConstraints.BOTH).weightx(1.0).done())
    add(PAUSE_BUTTON, GridBagBuilder().gridx(1).gridy(0).fill(GridBagConstraints.BOTH).weightx(1.0).done())
    add(PLAY_BUTTON, GridBagBuilder().gridx(2).gridy(0).fill(GridBagConstraints.BOTH).weightx(1.0).done())
  }
}

class ScreencastPlayerController(val zip: ScreencastZip) : AutoCloseable {

  private val script = zip.readSettings()[ScreencastZipSettings.SCRIPT_KEY]!!
  @Volatile
  private var server: PlayerServer? = null
  private var firstTime = true
  private val log = Logger("server")
  private val clientLog = Logger("client")

  @Volatile
  var serverReady = false
  @Volatile
  var clientReady = false

  private fun runServer() {
    thread {
      val server = ServerSocket(26000)
      log.info { "Trying accept..." }
      val socket = server.accept()
      log.info { "Accepted" }
      var firstTime = true
      this.server = PlayerServer(socket) { clientLog.info { it } }.apply {
        timeHandler = { this@ScreencastPlayerController.log.info { "Time: $it" } }
        eventHandler = {
          when (it) {
            PlayerServer.PLAY_CODE -> if (firstTime) {
              player.play(this@ScreencastPlayerController::handleError)
              firstTime = false
            } else {
              player.resume()
            }
            PlayerServer.PAUSE_CODE -> player.pause()
            PlayerServer.STOP_CODE -> player.stopImmediately()
            PlayerServer.END_CODE -> println("ENDED")
          }
        }
      }
      log.info { "Server ready" }
      serverReady = true
    }
  }

  private fun runClient() {
    CompileUtil.compileAndRun(script)
    log.info { "Client ready" }
    clientReady = true
  }

  private val player = zip.readSettings().let {
    if (zip.hasImportedAudio) {
      Player.create(
          it[ScreencastZipSettings.IMPORTED_EDITIONS_VIEW_KEY] ?: DefaultEditionsModel(),
          it[ScreencastZipSettings.IMPORTED_AUDIO_OFFSET_KEY] ?: 0) {
        zip.importedAudioInputStream
      }
    } else {
      Player.create(
          it[ScreencastZipSettings.PLUGIN_EDITIONS_VIEW_KEY] ?: DefaultEditionsModel(),
          it[ScreencastZipSettings.PLUGIN_AUDIO_OFFSET_KEY] ?: 0) {
        zip.audioInputStream
      }
    }
  }

  fun init() {
    runServer()
    runClient()
  }

  fun play() {
    if (firstTime) {
      firstTime = false
      server!!.play()
    } else {
      server!!.play()
    }
  }

  fun stop() {
    server!!.stop()
  }

  fun pause() {
    server!!.pause()
  }

  private fun handleError(ex: Throwable) {
    println("SERVER: $ex")
  }

  override fun close() {
    server.use {
      player.close()
    }
  }
}
