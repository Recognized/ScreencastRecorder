package com.github.recognized.screencast.recorder.actions

import com.github.recognized.screencast.recorder.sound.RecordingManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class StartRecordingAction : AnAction() {

  override fun actionPerformed(e: AnActionEvent) {
    RecordingManager.startRecording()
  }

  override fun update(e: AnActionEvent) {
//    e.presentation.isEnabled = !GlobalActionRecorder.isActive
  }
}
