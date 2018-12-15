package com.github.recognized.screencast.recorder

import com.intellij.testGuiFramework.recorder.GeneratedCodeReceiver

object GeneratedCodeReceiver : GeneratedCodeReceiver {
  private var myBuilder = StringBuilder()

  @Synchronized
  override fun receiveCode(code: String, indentation: Int) {
    val indent = "  " * indentation
    if (code != "}" && Timer.offsetToLastStatement >= 16) {
      myBuilder.append(indent)
      myBuilder.appendln(Timer.newTimeOffsetStatement())
    }
    myBuilder.append(indent)
    myBuilder.append(code)
    if (code == "}" && Timer.offsetToLastStatement >= 16) {
      myBuilder.append(indent)
      myBuilder.appendln(Timer.newTimeOffsetStatement())
    }
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