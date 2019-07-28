package com.github.recognized.screencast.recorder

import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JPanel
import javax.swing.Timer


class CountdownPanel(count: Int) : JPanel() {
    private val myCounter = AtomicInteger(count)
    private val myTimer = Timer(1000) {
        myCounter.decrementAndGet()
        if (myDeactivateNextTime.get()) {
            deactivate()
            return@Timer
        }
        repaint()
    }
    private var myDeactivateNextTime = AtomicBoolean(false)
    var deactivationAction: (() -> Unit)? = null
    
    init {
        isVisible = true
        isOpaque = false
    }
    
    private fun deactivate() {
        myTimer.stop()
        deactivationAction?.invoke()
    }
    
    override fun paint(g: Graphics?) {
        super.paint(g)
        g ?: return
        with(g as Graphics2D) {
            font = UIUtil.getLabelFont().deriveFont(Font.BOLD).deriveFont(JBUI.scale(100.0f))
            stroke = BasicStroke(JBUI.scale(4.0f))
            val metrics = getFontMetrics(font)
            val count = myCounter.get()
            val str = if (count > 0) "" + count else "GO"
            if (count == 0) {
                myDeactivateNextTime.compareAndSet(false, true)
            }
            val gv = font.layoutGlyphVector(fontRenderContext, str.toCharArray(), 0, str.length, 0)
            val shape = gv.outline
            Color.BLACK
            translate(
                    width / 2 - metrics.stringWidth(str) / 2,
                    height / 3 + metrics.height
            )
            draw(shape)
            color = Color.YELLOW
            drawString(str, 0, 0)
        }
    }
    
    fun countDown() {
        myTimer.start()
    }
}