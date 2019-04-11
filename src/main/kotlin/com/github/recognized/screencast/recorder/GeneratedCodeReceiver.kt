package com.github.recognized.screencast.recorder

import com.intellij.testGuiFramework.framework.GuiTestUtil
import com.intellij.testGuiFramework.recorder.GeneratedCodeReceiver
import kotlin.math.max

object GeneratedCodeReceiver : GeneratedCodeReceiver {
  private var myBuilder = StringBuilder()

  @Synchronized
  override fun receiveCode(code: String, indentation: Int) {
    val indent = "  " * indentation
    val time = Timer.offsetToLastStatement
    if (code != "}" && Timer.offsetToLastStatement >= 16) {
      myBuilder.append(indent)
      myBuilder.appendln(Timer.newTimeOffsetStatement())
    }
    myBuilder.append(indent)
    val x = code.toLowerCase()
    if (x.contains("com.github.recognized.screencast.recorder", true) || (x.contains("menu") && x.contains("\"screencast recorder\""))) {
      myBuilder.append("// skipped $code")
    } else {
      myBuilder.append(doMagic(code, time))
    }
    if (code == "}" && Timer.offsetToLastStatement >= 16) {
      myBuilder.append(indent)
      myBuilder.appendln(Timer.newTimeOffsetStatement())
    }
  }

  val r = "typeText\\(\"(.*)\"\\)".toRegex()
  val bsp = ".Backspace".toRegex()

  private fun doMagic(code: String, time: Long): String {
    return if (code.trim().matches(r)) {
      return try {
        var x = r.findAll(code.trim()).toList()[0].groupValues[1]
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("Tab", "")
        println(x)
        while (x.contains(bsp)) {
          x = x.replaceFirst(bsp, "")
        }
        var enters = ""
        while (x.startsWith("Enter")) {
          x = x.removePrefix("Enter")
//          enters += "\nshortcut(ENTER)"
        }
        "GuiTestUtil.typeText(\"$x\", robot(), ${32})\n"
      } catch (ex: Throwable) {
        println(ex)
        code
      }
    } else code
  }

  @Synchronized
  fun getAndFlush(): String {
    return myBuilder.toString().also { myBuilder = StringBuilder() }
  }
}

infix operator fun String.times(multiplier: Int): String {
  return buildString {
    for (i in 1..multiplier) {
      append(this@times)
    }
  }
}