package com.github.recognized.screencast.player

import com.intellij.testGuiFramework.recorder.compile.ScriptWrapper

object MyScriptWrapper {
    
    fun wrapScript(code: String): String {
        return """
      import com.github.recognized.screencast.player.*

      ${ScriptWrapper.wrapScript(
                """
          with(___connectClient()) {
            while (true) {
              try {
                ___start()
                $code
              } catch(ex: StopClient) {
                continue
              } catch(ex: CloseClient) {
                return@with
              } catch (ex: Throwable) {
                ___codeError(ex)
              }
              ___end()
            }
          }
          """)}
    """
    }
}