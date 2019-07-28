package com.github.recognized.screencast.recorder.format

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.vfs.VirtualFile
import icons.ScreencastRecorderIcons
import javax.swing.Icon

object ScreencastFileType : FileType {
    override fun getDefaultExtension() = "scs"
    
    override fun getIcon(): Icon = ScreencastRecorderIcons.SCREENCAST
    
    override fun getCharset(file: VirtualFile, content: ByteArray): String? = null
    
    override fun getName() = "Screencast"
    
    override fun getDescription() = "Screencast Data File"
    
    override fun isBinary() = true
    
    override fun isReadOnly() = true
    
    val dotExtension = ".$defaultExtension"
}