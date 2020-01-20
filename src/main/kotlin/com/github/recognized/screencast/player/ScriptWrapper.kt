package com.github.recognized.screencast.player

object MyScriptWrapper {
    
    const val CLASS_NAME = "ScreencastScenarioMain"
    const val METHOD_NAME = "scenario"
    
    fun wrapScript(code: String): String {
        return """
            import com.github.recognized.screencast.player.Scenario
            import com.github.recognized.screencast.player.CoroutinePlayer
            
            class $CLASS_NAME() {
                fun $METHOD_NAME(): Scenario {
                    return object : Scenario {
                        override fun play(__player: CoroutinePlayer) {
                            with(__player) {
                                $code
                            }
                        }
                    }
                }
            }
        """.trimMargin()
    }
}