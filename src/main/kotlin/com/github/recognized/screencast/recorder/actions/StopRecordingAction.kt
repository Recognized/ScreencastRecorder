package com.github.recognized.screencast.recorder.actions

import com.github.recognized.screencast.recorder.sound.RecordingManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ShortcutSet

//fun x() {
//  ActionManager.getInstance().registerAction()
//}

class StopRecordingAction : AnAction() {

  override fun actionPerformed(e: AnActionEvent) {
    RecordingManager.stopRecording()
  }

  override fun update(e: AnActionEvent) {
//    e.presentation.isEnabled = GlobalActionRecorder.isActive
  }

  override fun setShortcutSet(shortcutSet: ShortcutSet) {
    super.setShortcutSet(shortcutSet)
  }
}
