package com.github.recognized.screencast.recorder.sound

import com.github.recognized.kotlin.ranges.extensions.length
import com.intellij.openapi.application.ApplicationManager
import java.io.InputStream
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine
import kotlin.concurrent.thread
import kotlin.math.min

interface Player : AutoCloseable {

  fun setOnStopAction(action: () -> Unit)

  fun getFramePosition(): Long

  fun play(errorHandler: (Throwable) -> Unit)

  fun resume()

  fun pause()

  fun stop()

  fun stopImmediately()

  companion object {

    fun create(
      editions: EditionsView,
      offsetFrames: Long,
      getAudioStream: () -> InputStream
    ): Player {
      return PlayerImpl(editions, offsetFrames, getAudioStream)
    }
  }

  private class PlayerImpl(
    editions: EditionsView,
    private val offsetFrames: Long,
    private val getAudioStream: () -> InputStream
  ) : Player {
    private val mySource: SourceDataLine
    private var myOnStopAction: () -> Unit = {}
    private var mySignalStopReceived = false
    private val myEditionModel = editions.copy()

    init {
      val fileFormat = SoundProvider.getAudioFileFormat(getAudioStream().buffered())
      val decodedFormat = SoundProvider.getWaveformPcmFormat(fileFormat.format)
      mySource = AudioSystem.getSourceDataLine(decodedFormat)
      if (offsetFrames < 0) {
        myEditionModel.cut(0 until -offsetFrames)
      }
    }

    override fun pause() {
      mySource.stop()
    }

    override fun stop() {
      mySignalStopReceived = true
      mySource.drain()
      mySource.flush()
      mySource.stop()
    }

    override fun stopImmediately() {
      mySignalStopReceived = true
      mySource.stop()
      mySource.flush()
    }

    override fun close() {
      try {
        mySource.close()
      } catch (ex: Throwable) {
      }
    }

    override fun resume() {
      mySource.start()
    }

    override fun setOnStopAction(action: () -> Unit) {
      myOnStopAction = action
    }

    override fun getFramePosition(): Long {
      return mySource.longFramePosition
    }

    override fun play(errorHandler: (Throwable) -> Unit) {
      mySource.start()
      thread(start = true) {
        SoundProvider.withWaveformPcmStream(getAudioStream()) { inputStream ->
          try {
            applyEditionImpl(inputStream)
          } catch (ex: Throwable) {
            ApplicationManager.getApplication().invokeLater { errorHandler(ex) }
          } finally {
            myOnStopAction()
          }
        }
      }
    }

    private fun applyEditionImpl(decodedStream: AudioInputStream) {
      val editions = myEditionModel.editionsModel
      if (!mySource.isOpen) {
        mySource.open(decodedStream.format)
      }
      ApplicationManager.getApplication().invokeLater { mySource.start() }
      val buffer = ByteArray(1 shl 14)
      val frameSize = decodedStream.format.frameSize
      if (offsetFrames > 0) {
        buffer.fill(0)
        var needBytes = offsetFrames * frameSize
        while (needBytes != 0L) {
          val zeroesCount = min(buffer.size.toLong(), needBytes).toInt().modFloor(frameSize)
          writeOrBlock(buffer, zeroesCount)
          needBytes -= zeroesCount
        }
      }
      outer@ for (edition in editions) {
        var needBytes = edition.first.length * frameSize
        when (edition.second) {
          EditionsModel.EditionType.CUT -> {
            while (needBytes != 0L && !mySignalStopReceived) {
              SoundProvider.skipFrames(decodedStream, buffer, edition.first.length)
              needBytes = 0L
            }
          }
          EditionsModel.EditionType.MUTE -> {
            buffer.fill(0)
            var needSkip = needBytes
            while (needBytes != 0L || needSkip != 0L) {
              if (needBytes != 0L) {
                val zeroesCount = min(buffer.size.toLong(), needBytes).toInt().modFloor(frameSize)
                if (mySignalStopReceived) {
                  break@outer
                }
                writeOrBlock(buffer, zeroesCount)
                needBytes -= zeroesCount
              }
              if (needSkip != 0L) {
                SoundProvider.skipFrames(decodedStream, buffer, edition.first.length)
                needSkip = 0L
                buffer.fill(0)
              }
            }
          }
          EditionsModel.EditionType.NO_CHANGES -> {
            while (needBytes != 0L) {
              val read = decodedStream.read(buffer, 0, min(buffer.size.toLong(), needBytes).toInt())
              if (read == -1 || mySignalStopReceived) {
                break@outer
              }
              needBytes -= read
              writeOrBlock(buffer, read)
            }
          }
        }
      }
    }

    fun Int.modFloor(modulus: Int): Int {
      return this - this % modulus
    }

    private fun writeOrBlock(buffer: ByteArray, size: Int) {
      var needWrite = size
      while (needWrite != 0) {
        val written = mySource.write(buffer, size - needWrite, needWrite)
        needWrite -= written
      }
    }
  }
}
