package com.github.recognized.screencast.player

import com.github.recognized.screencast.recorder.format.ScreencastZip
import com.github.recognized.screencast.recorder.format.ScreencastZipSettings
import com.github.recognized.screencast.recorder.sound.Player
import com.github.recognized.screencast.recorder.sound.impl.DefaultEditionsModel
import com.github.recognized.screencast.recorder.util.GridBagBuilder
import com.github.recognized.screencast.recorder.util.grid
import com.github.recognized.screencast.runtime.Property
import com.github.recognized.screencast.runtime.awaitValue
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import icons.ScreencastRecorderIcons
import java.awt.*
import java.nio.file.Path
import javax.swing.*

class Logger(val name: String) {
    
    fun info(f: () -> Any) {
        println("[$name] " + f())
    }
}

class PauseScreencast : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        PlayerWindow.Instance?.player?.controller?.value?.pause()
    }
}

class PlayScreencast : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        PlayerWindow.Instance?.player?.controller?.value?.play()
    }
}

class StopScreencast : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        PlayerWindow.Instance?.player?.nextController?.invoke()
    }
}


fun showController(screencast: Path) {
    ProgressManager.getInstance().run(object : Task.Backgroundable(null, "Preparing screencast", false) {
        override fun run(indicator: com.intellij.openapi.progress.ProgressIndicator) {
            indicator.isIndeterminate = true
            if (!indicator.isRunning) {
                indicator.start()
            }
            try {
                val zip = ScreencastZip(screencast)
                val scenario = CompileUtil.compile(zip.readSettings()[ScreencastZipSettings.SCRIPT_KEY]!!)
                ApplicationManager.getApplication().invokeLater {
                    PlayerWindow.newInstance(scenario, zip)
                }
            } finally {
                if (indicator.isRunning) {
                    indicator.stop()
                }
            }
        }
    })
    
}

class PlayerWindow private constructor(scenario: Scenario, zip: ScreencastZip) : JFrame(zip.path.toString()), Disposable {
    
    val player = ScreencastPlayerView(this, scenario, zip, zip.readSettings()[ScreencastZipSettings.TOTAL_LENGTH_PLUGIN]
            ?: 0)
    
    
    init {
        player.initComponent()
        add(player)
        size = Dimension(JBUI.pixScale(600f).toInt(), JBUI.pixScale(80f).toInt())
        player.size = size
        isVisible = true
        isResizable = false
//        this.addWindowListener(object : WindowAdapter() {
//            override fun windowClosing(e: WindowEvent) {
//
//            }
//        })
    }
    
    companion object {
        var Instance: PlayerWindow? = null
            private set
        
        fun newInstance(scenario: Scenario, zip: ScreencastZip): PlayerWindow {
            Instance?.let(Disposer::dispose)
            return PlayerWindow(scenario, zip).also {
                Instance = it
            }
        }
    }
}


class ScreencastPlayerView(parent: Disposable, private val scenario: Scenario, private val zip: ScreencastZip, totalLength: Long) : JPanel() {
    val controller = Property<ScreencastPlayerController?>(null)
    val nextController: () -> Unit = run {
        var currentDisposable = Disposer.newDisposable()
        return@run {
            controller.value?.stop()
            controller.value?.let(Disposer::dispose)
            controller.value = null
            Disposer.dispose(currentDisposable)
            val c = Disposer.newDisposable()
            Disposer.register(parent, c)
            currentDisposable = c
            ApplicationManager.getApplication().executeOnPooledThread {
                val player = createPlayer(zip)
                ApplicationManager.getApplication().invokeLater {
                    if (!Disposer.isDisposed(c)) {
                        controller.value = ScreencastPlayerController(player, scenario).also {
                            it.state.forEach(it) {
                                updateButtons()
                            }
                        }
                    }
                }
            }
        }
    }
    
    val timer = Timer(1000 / 60) {
        progress.value = (controller.value?.pos ?: 0L).toDouble()
    }
    
    init {
        nextController()
        parent.add {
            controller.value?.terminate()
            timer.stop()
        }
    }
    
    private val stop by lazy {
        JButton(ScreencastRecorderIcons.STOP).apply {
            addActionListener {
                nextController()
            }
        }
    }
    
    private val pause by lazy {
        JButton(ScreencastRecorderIcons.PAUSE).apply {
            addActionListener {
                controller.value?.pause()
            }
        }
    }
    
    private val play by lazy {
        JButton(ScreencastRecorderIcons.PLAY).apply {
            addActionListener {
                controller.value?.play()
            }
        }
    }
    
    private val progress by lazy {
        val x = ProgressIndicator(totalLength.toDouble())
        x.border = BorderFactory.createEmptyBorder(5, 0, 5, 0)
        x
    }
    
    
    private var initialized = false
    
    fun initComponent() {
        layout = GridBagLayout()
        val buttons = JPanel(GridBagLayout()).apply {
            add(JPanel(), GridBagBuilder().gridx(0).gridy(0).fill(GridBagConstraints.BOTH).weightx(1.0).weighty(1.0).done())
            add(stop, GridBagBuilder().gridx(1).gridy(0).fill(GridBagConstraints.BOTH).weightx(0.0).done())
            add(pause, GridBagBuilder().gridx(2).gridy(0).fill(GridBagConstraints.BOTH).weightx(0.0).done())
            add(play, GridBagBuilder().gridx(3).gridy(0).fill(GridBagConstraints.BOTH).weightx(0.0).done())
            add(JPanel(), GridBagBuilder().gridx(4).gridy(0).fill(GridBagConstraints.BOTH).weightx(1.0).weighty(1.0).done())
        }
        buttons.background = UIUtil.getEditorPaneBackground()
        add(progress, grid(x = 0, y = 0, wx = 1.0, wy = 1.0))
        add(buttons, grid(x = 0, y = 1, wx = 1.0, wy = 1.0))
        initialized = true
        timer.start()
    }
    
    private fun updateButtons() {
        if (initialized) {
            val state = controller.value?.state?.value
            stop.isEnabled = state is Play || state is Pause
            play.isEnabled = state is Idle || state is Pause
            pause.isEnabled = state is Play
        }
    }
}

class ProgressIndicator(private val maxValue: Double) : JPanel() {
    
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

class ScreencastPlayerController(private val audioPlayer: Player, scenario: Scenario) : Disposable by Disposer.newDisposable() {
    private val scriptPlayer = CoroutinePlayer(this, scenario) {
        ApplicationManager.getApplication().invokeLater {
            Messages.showErrorDialog(null, it.toString(), "Script execution error")
        }
    }
    
    init {
        Disposer.register(this, Disposable {
            audioPlayer.stopImmediately()
            audioPlayer.close()
        })
    }
    
    val state = scriptPlayer.internalState
    val pos get() = audioPlayer.getFramePosition()
    
    fun play() {
        scriptPlayer.signal.value = PlaySignal.PLAY
        audioPlayer.play {
            ApplicationManager.getApplication().invokeLater {
                Messages.showErrorDialog(null, it.toString(), "Audio error")
            }
        }
    }
    
    fun pause() {
        launch {
            scriptPlayer.signal.value = PlaySignal.PAUSE
            scriptPlayer.internalState.awaitValue(this@ScreencastPlayerController) {
                it is Pause
            }
            audioPlayer.pause()
        }
    }
    
    fun stop() {
        launch {
            scriptPlayer.signal.value = PlaySignal.STOP
            scriptPlayer.internalState.awaitValue(this@ScreencastPlayerController) {
                it is Stop
            }
            audioPlayer.stopImmediately()
        }
    }
}

fun Disposable.add(fn: () -> Unit) {
    Disposer.register(this, Disposable(fn))
}

fun Disposable.terminate() {
    Disposer.dispose(this)
}

private fun createPlayer(zip: ScreencastZip): Player {
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
