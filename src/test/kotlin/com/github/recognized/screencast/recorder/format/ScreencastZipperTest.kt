package com.github.recognized.screencast.recorder.format

import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.junit.After
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest
import java.util.*

class ScreencastZipperTest : LightCodeInsightFixtureTestCase() {

  private val myTempFile = Files.createTempFile(
    javaClass.name.replace('\\', '.'),
    ".${ScreencastFileType.defaultExtension}"
  )
  private val myPluginAudioPath = RESOURCES_PATH.resolve("demo.wav")
  private val myImportedAudioPath = RESOURCES_PATH.resolve("demo.mp3")


  fun `test screencast is consistent`() {
    ScreencastZipper.createZip(myTempFile) {
      addPluginAudio(Files.newInputStream(myPluginAudioPath))
      addImportedAudio(Files.newInputStream(myImportedAudioPath))
    }
    val zip = ScreencastZip(myTempFile)
    assertEquals(ScreencastZipSettings(), zip.readSettings())
    assertTrue(zip.hasImportedAudio and zip.hasPluginAudio)
    assertEquals(
      Files.newInputStream(myPluginAudioPath).buffered().sha1sum(),
      zip.audioInputStream.sha1sum()
    )
    assertEquals(
      Files.newInputStream(myImportedAudioPath).buffered().sha1sum(),
      zip.importedAudioInputStream.sha1sum()
    )
  }

  @After
  fun after() {
    Files.delete(myTempFile)
  }

  private fun InputStream.sha1sum(): String {
    val buffer = ByteArray(1 shl 14)
    val summer = MessageDigest.getInstance("SHA-1")
    var read: Int
    while (true) {
      read = read(buffer)
      if (read < 0) {
        break
      }
      summer.update(buffer, 0, read)
    }
    return Base64.getEncoder().encodeToString(summer.digest())
  }

  companion object {
    val RESOURCES_PATH: Path = Paths.get("src", "test", "resources")
  }
}
