package com.github.recognized.screencast.recorder.format

import com.intellij.openapi.diagnostic.logger
import com.github.recognized.screencast.recorder.sound.EditionsModel
import com.github.recognized.screencast.recorder.sound.EditionsView
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.xml.bind.JAXB
import javax.xml.bind.annotation.adapters.XmlAdapter

object ScreencastZipper {
  private val LOG = logger<ScreencastZipper>()

  fun createZip(out: Path, action: ZipperScope.() -> Unit) {
    ZipperScope(out).use(action)
  }

  @Suppress("unused")
  class ZipperScope(val out: Path) : AutoCloseable {
    private val myZipStream = ZipOutputStream(Files.newOutputStream(out).buffered())
    private val myEntrySet = mutableSetOf<EntryType>()
    var settings = ScreencastZipSettings()

    init {
      myZipStream.setLevel(0)
      myZipStream.setMethod(ZipOutputStream.DEFLATED)
    }

    fun useAudioOutputStream(isPlugin: Boolean, name: String? = null, block: (OutputStream) -> Unit) {
      val entry = if (isPlugin) EntryType.PLUGIN_AUDIO else EntryType.IMPORTED_AUDIO
      if (!myEntrySet.add(entry)) {
        throw IllegalStateException("Audio is already zipped.")
      }
      val zipEntry = ZipEntry(name ?: "audio_"+entry.name)
      zipEntry.comment = entry.name
      myZipStream.putNextEntry(zipEntry)
      val outputStream = object : OutputStream() {
        override fun write(b: Int) {
          myZipStream.write(b)
        }

        override fun write(b: ByteArray?) {
          myZipStream.write(b)
        }

        override fun write(b: ByteArray?, off: Int, len: Int) {
          myZipStream.write(b, off, len)
        }

        override fun close() {
          myZipStream.closeEntry()
        }
      }
      outputStream.buffered().use(block)
    }

    fun addPluginAudio(inputStream: InputStream) {
      useAudioOutputStream(isPlugin = true) { output ->
        inputStream.buffered().use { input ->
          input.transferTo(output)
        }
      }
    }

    fun addImportedAudio(inputStream: InputStream) {
      useAudioOutputStream(isPlugin = false) { output ->
        inputStream.buffered().use { input ->
          input.transferTo(output)
        }
      }
    }

    fun addScript(script: String) {
      settings[ScreencastZipSettings.SCRIPT_KEY] = script
    }

    fun addPluginEditionsView(editionsView: EditionsView?) {
      settings[ScreencastZipSettings.PLUGIN_EDITIONS_VIEW_KEY] = editionsView
    }

    fun addImportedEditionsView(editionsView: EditionsView?) {
      settings[ScreencastZipSettings.IMPORTED_EDITIONS_VIEW_KEY] = editionsView
    }

    fun setImportedAudioOffset(offsetFrames: Long) {
      settings[ScreencastZipSettings.IMPORTED_AUDIO_OFFSET_KEY] = offsetFrames
    }

    fun setPluginAudioOffset(offsetFrames: Long) {
      settings[ScreencastZipSettings.PLUGIN_AUDIO_OFFSET_KEY] = offsetFrames
    }

    private fun writeEntry(name: String, type: EntryType, data: ByteArray) {
      with(myZipStream) {
        val entry = ZipEntry(name)
        entry.comment = type.name
        putNextEntry(entry)
        write(data)
        closeEntry()
      }
    }

    private fun saveSettings() {
      val stream = ByteArrayOutputStream()
      JAXB.marshal(settings, stream)
      writeEntry(out.fileName.toString() + ".settings", EntryType.SETTINGS, stream.toByteArray())
    }

    override fun close() {
      try {
        saveSettings()
        myZipStream.close()
      } catch (ex: Exception) {
        LOG.info(ex)
      }
    }

  }

  private class EditionsViewAdapter : XmlAdapter<String, EditionsView>() {
    override fun marshal(v: EditionsView): String {
      return Base64.getEncoder().encodeToString(v.serialize())
    }

    override fun unmarshal(v: String): EditionsView {
      return EditionsModel.deserialize(Base64.getDecoder().decode(v))
    }
  }

  // Exact implementation from Java 9 JDK
  @Throws(IOException::class)
  private fun InputStream.transferTo(out: OutputStream): Long {
    var transferred: Long = 0
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var read: Int
    while (true) {
      read = this.read(buffer, 0, DEFAULT_BUFFER_SIZE)
      if (read < 0) break
      out.write(buffer, 0, read)
      transferred += read.toLong()
    }
    return transferred
  }

  enum class EntryType {
    PLUGIN_AUDIO,
    IMPORTED_AUDIO,
    SETTINGS
  }
}