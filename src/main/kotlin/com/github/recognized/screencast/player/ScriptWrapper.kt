package com.github.recognized.screencast.player

import com.intellij.testGuiFramework.recorder.compile.ScriptWrapper

object MyScriptWrapper {

  fun wrapScript(code: String): String {
    return imports + "\n" + ScriptWrapper.wrapScript(
        """

    try {
     with(___connectClient()) {
       ___start()
       $code
       ___end()
        }
     } catch (ex: Throwable) {
       ___error(ex)
     }
        """
    ) + "\n" + inlineLib
  }
}