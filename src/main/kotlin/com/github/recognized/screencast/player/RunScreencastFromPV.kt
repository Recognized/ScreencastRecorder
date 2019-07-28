package com.github.recognized.screencast.player

import com.github.recognized.screencast.recorder.format.ScreencastFileType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.vfs.VirtualFile
import java.io.File
import java.nio.file.Path

class RunScreencastFromPV : AnAction() {
    
    override fun actionPerformed(e: AnActionEvent) {
        val file = e.getRequiredData(CommonDataKeys.VIRTUAL_FILE)
        showController(file.toPath())
    }
    
    override fun update(e: AnActionEvent) {
        super.update(e)
        e.presentation.isEnabledAndVisible = e.presentation.isEnabledAndVisible
                && e.project != null
                && e.getData(CommonDataKeys.VIRTUAL_FILE)?.fileType == ScreencastFileType
    }
}

class RunScreencastFromFileChooser : AnAction() {
    
    override fun actionPerformed(e: AnActionEvent) {
        val descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor(ScreencastFileType)
        FileChooser.chooseFile(descriptor, null, null) {
            open(it)
        }
    }
    
    private fun open(file: VirtualFile) {
        ApplicationManager.getApplication().invokeLater {
            showController(file.toPath())
        }
    }
}

fun VirtualFile.toPath(): Path {
    return File(this.path).toPath()
}