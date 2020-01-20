package com.github.recognized.screencast.player

interface Scenario {
    suspend fun play(player: CoroutinePlayer)
}
