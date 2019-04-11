package com.github.recognized.screencast.player

import com.github.recognized.screencast.recorder.format.ScreencastZip
import com.github.recognized.screencast.recorder.format.ScreencastZipSettings
import com.github.recognized.screencast.recorder.sound.Player
import com.github.recognized.screencast.recorder.sound.impl.DefaultEditionsModel
import com.github.recognized.screencast.recorder.util.GridBagBuilder
import com.github.recognized.screencast.recorder.util.grid
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import icons.ScreencastRecorderIcons
import java.awt.*
import java.net.ServerSocket
import java.nio.file.Path
import javax.swing.*
import kotlin.concurrent.thread
import javax.swing.JOptionPane
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.event.WindowListener
import java.lang.Math.abs


class Logger(val name: String) {

  fun info(f: () -> Any) {
    if (enabled) {
      println("[$name] " + f())
    }
  }
}

const val enabled = true

@Volatile
var errorOccurred = false

class PauseScreencast : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    currentController?.pause()
  }
}

class PlayScreencast : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    currentController?.play()
  }
}

class StopScreencast : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    currentController?.reset()
  }
}

@Volatile
var currentController: ScreencastPlayerController? = null
@Volatile
var currentWindow: PlayerWindow? = null

@Synchronized
fun shutdown() {
  errorOccurred = false
  currentController?.stop()
  currentController = null
  currentWindow?.dispose()
  currentWindow?.isVisible = false
  currentWindow?.let { it.dispatchEvent(WindowEvent(it, WindowEvent.WINDOW_CLOSING)) }
  currentWindow = null
}

fun showController(screencast: Path) {
  shutdown()
  ProgressManager.getInstance().run(object : Task.Backgroundable(null, "Preparing screencast", false) {
    override fun run(indicator: com.intellij.openapi.progress.ProgressIndicator) {
      indicator.isIndeterminate = true
      if (!indicator.isRunning) {
        indicator.start()
      }
      try {
        val zip = ScreencastZip(screencast)
        val controller = ScreencastPlayerController(zip)
        controller.awaitInit(60000)
        ApplicationManager.getApplication().invokeLater {
          if (!errorOccurred) {
            currentController = controller
            currentWindow = PlayerWindow(zip, controller)
          }
          errorOccurred = false
        }
      } finally {
        if (indicator.isRunning) {
          indicator.stop()
        }
      }
    }
  })

}

class PlayerWindow(zip: ScreencastZip, controller: ScreencastPlayerController) : JFrame(zip.path.toString()) {

  private val player by lazy {
    ScreencastPlayerView(controller, zip.readSettings()[ScreencastZipSettings.TOTAL_LENGTH_PLUGIN] ?: 0)
  }

  init {
    player.initComponent()
    add(player)
    size = Dimension(JBUI.pixScale(600f).toInt(), JBUI.pixScale(80f).toInt())
    player.size = size
    isVisible = true
    isResizable = false
    this.addWindowListener(object : WindowListener {
      override fun windowClosing(e: WindowEvent) {
        dispose()
      }

      override fun windowDeiconified(e: WindowEvent?) {
      }

      override fun windowClosed(e: WindowEvent?) {
      }

      override fun windowActivated(e: WindowEvent?) {
      }

      override fun windowDeactivated(e: WindowEvent?) {
      }

      override fun windowOpened(e: WindowEvent?) {
      }

      override fun windowIconified(e: WindowEvent?) {
      }
    })
  }

  override fun dispose() {
    try {
      player.controller.close()
    } catch (ex: Throwable) {
    }
    currentController = null
  }
}

fun ScreencastPlayerController.awaitInit(timeout: Long) {
  val time = System.currentTimeMillis()
  init()
  while (!serverReady && System.currentTimeMillis() < time + timeout && !errorOccurred) {
    Thread.sleep(16)
  }
  if (!serverReady) {
    throw IllegalStateException("Cannot connect to the screencast reproducer. Timeout: ${timeout}s")
  }
}


class ScreencastPlayerView(val controller: ScreencastPlayerController, totalLength: Long) : JPanel() {

  private val STOP_BUTTON by lazy {
    JButton(ScreencastRecorderIcons.STOP).apply {
      addActionListener {
        controller.reset()
      }
      isEnabled = false
    }
  }

  private val PAUSE_BUTTON by lazy {
    JButton(ScreencastRecorderIcons.PAUSE).apply {
      addActionListener {
        controller.pause()
      }
      isEnabled = false
    }
  }

  private val PLAY_BUTTON by lazy {
    JButton(ScreencastRecorderIcons.PLAY).apply {
      addActionListener {
        controller.play()
      }
    }
  }

  private val PROGRESS by lazy {
    val x = ProgressIndicator(totalLength.toDouble())
    x.border = BorderFactory.createEmptyBorder(5, 0, 5, 0)
    x
  }

  val timer = Timer(1000 / 60) {
    PROGRESS.value = controller.pos.toDouble()
  }.also { it.start() }

  fun initComponent() {
    layout = GridBagLayout()
    val buttons = JPanel(GridBagLayout()).apply {
      add(JPanel(), GridBagBuilder().gridx(0).gridy(0).fill(GridBagConstraints.BOTH).weightx(1.0).weighty(1.0).done())
      add(STOP_BUTTON, GridBagBuilder().gridx(1).gridy(0).fill(GridBagConstraints.BOTH).weightx(0.0).done())
      add(PAUSE_BUTTON, GridBagBuilder().gridx(2).gridy(0).fill(GridBagConstraints.BOTH).weightx(0.0).done())
      add(PLAY_BUTTON, GridBagBuilder().gridx(3).gridy(0).fill(GridBagConstraints.BOTH).weightx(0.0).done())
      add(JPanel(), GridBagBuilder().gridx(4).gridy(0).fill(GridBagConstraints.BOTH).weightx(1.0).weighty(1.0).done())
    }
    buttons.background = UIUtil.getEditorPaneBackground()
    add(PROGRESS, grid(x = 0, y = 0, wx = 1.0, wy = 1.0))
    add(buttons, grid(x = 0, y = 1, wx = 1.0, wy = 1.0))
    controller.stateChanged = { _, newState ->
      when (newState) {
        PlayerServer.State.PLAY -> {
          STOP_BUTTON.isEnabled = true
          PLAY_BUTTON.isEnabled = false
          PAUSE_BUTTON.isEnabled = true
          started()
        }
        PlayerServer.State.PAUSE -> {
          PLAY_BUTTON.isEnabled = true
          STOP_BUTTON.isEnabled = true
          PAUSE_BUTTON.isEnabled = false
          paused()
        }
        PlayerServer.State.STOP -> {
          stopped()
        }
        PlayerServer.State.END -> {
          if (controller.playerEnded) {
            STOP_BUTTON.isEnabled = false
            PAUSE_BUTTON.isEnabled = false
            PLAY_BUTTON.isEnabled = true
            ended()
          }
        }
        PlayerServer.State.IDLE -> {
          STOP_BUTTON.isEnabled = false
          PAUSE_BUTTON.isEnabled = false
          PLAY_BUTTON.isEnabled = true
          reset()
        }
      }
    }
    controller.playerClosed = {
      if (controller.state == PlayerServer.State.END) {
        STOP_BUTTON.isEnabled = false
        PAUSE_BUTTON.isEnabled = false
        PLAY_BUTTON.isEnabled = true
        ended()
      }
    }
  }

  fun ended() {

  }

  fun stopped() {

  }

  fun paused() {

  }

  fun started() {

  }

  fun reset() {

  }
}

class ProgressIndicator(val maxValue: Double) : JPanel() {

  var value: Double = 0.0
    set(value) {
      field = value
      repaint()
    }

  override fun paint(g: Graphics) {
    with(g as Graphics2D) {
      color = REST
      fillRect(0, 0, width, height)
      color = PASSED
      if (maxValue != 0.0) {
        fillRect(0, 0, Math.ceil(width * value / maxValue).toInt(), height)
      }
    }
  }

  companion object {
    val PASSED = JBColor.link()
    val REST: Color = JBColor(UIUtil.getEditorPaneBackground().darker(), UIUtil.getListBackground().brighter())
  }
}

class ScreencastPlayerController(val zip: ScreencastZip) : AutoCloseable {

  private val script = zip.readSettings()[ScreencastZipSettings.SCRIPT_KEY]!!
  @Volatile
  private var server: PlayerServer? = null
  private val log = Logger("server")
  private val clientLog = Logger("client")

  @Volatile
  var state: PlayerServer.State = PlayerServer.State.IDLE
    set(value) {
      stateChanged(field, value)
      field = value
    }

  @Volatile
  private var player: Player? = createPlayer()
    set(value) {
      try {
        field?.close()
      } catch (ex: Throwable) {
      }
      field = value
    }


  @Volatile
  var playerEnded = false
    set(value) {
      field = value
      if (value) {
        playerClosed()
      }
    }

  @Volatile
  var playerClosed: () -> Unit = {}

  @Volatile
  var stateChanged: (old: PlayerServer.State, new: PlayerServer.State) -> Unit = { _, _ -> }

  @Volatile
  var serverReady = false

  private fun runServer() {
    thread {
      val server = ServerSocket(26000)
      val socket = server.accept()
      log.info { "Accepted" }
      this.server = PlayerServer(socket).apply {
        timeHandler = {
          log.info { "Time: ${it.toDouble().div(1000)}, Player: ${player?.elapsedMillis?.div(1000)}s" }
          player?.let { player ->
            if (!player.stopped) {
              val d = it - player.elapsedMillis.toLong()
              if (abs(d) >= 32) {
                delay(d)
              }
            }
          }
        }
        logger = { clientLog.info { it } }
        codeError = {
          shutdown()
          ApplicationManager.getApplication().invokeLater {
            Messages.showErrorDialog(null, it, "Script execution error")
          }
        }
        stateChanged = { _, newState ->
          when (newState) {
            PlayerServer.State.PLAY -> player?.let {
              if (!it.started) {
                it.play { log.info { it } }
              } else {
                it.resume()
              }
            }
            PlayerServer.State.PAUSE -> player?.pause()
            PlayerServer.State.IDLE -> {
              player?.stopImmediately()
              player = createPlayer().apply {
                setOnStopAction { playerEnded = true }
                state = PlayerServer.State.END
              }
            }
            PlayerServer.State.END -> {
              reset()
            }
            PlayerServer.State.STOP -> {
            }
          }
          state = newState
        }
      }
      serverReady = true
    }
  }

  private fun runClient() {
    thread {
      CompileUtil.compileAndRun(script)
    }
  }

  private fun createPlayer(): Player {
    return zip.readSettings().let {
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
  }

  val pos get() = player?.getFramePosition() ?: 0

  fun init() {
    runServer()
    runClient()
  }

  fun play() {
    server!!.play()
  }

  fun stop() {
    server!!.stop()
  }

  fun pause() {
    server!!.pause()
  }

  fun reset() {
    server!!.reset()
  }

  fun delay(value: Long) {
    server!!.correctTime(value)
  }

  override fun close() {
    try {
      server?.stop()
    } catch (ex: Throwable) {
      log.info { ex }
    }
    try {
      server.use {
        player?.close()
      }
    } catch (ex: Throwable) {
      log.info { ex }
    }
  }
}
