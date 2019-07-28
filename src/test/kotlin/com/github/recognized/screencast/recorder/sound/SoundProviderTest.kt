package com.github.recognized.screencast.recorder.sound

import com.github.recognized.screencast.recorder.format.ScreencastZipperTest.Companion.RESOURCES_PATH
import org.junit.Test
import java.io.InputStream
import java.nio.file.Files
import javax.sound.sampled.AudioFormat
import kotlin.test.assertFails
import kotlin.test.assertTrue
import kotlin.test.fail

class SoundProviderTest {
    
    @Test
    fun `test supported audio file`() {
        for (file in SUPPORTED_FILES) {
            try {
                SoundProvider.getAudioInputStream(file)
            } catch (_: Throwable) {
                fail("Unsupported file: $file")
            }
        }
    }
    
    @Test
    fun `test supported audio input stream`() {
        for (file in SUPPORTED_FILES) {
            Files.newInputStream(file.toPath()).use {
                SoundProvider.getAudioInputStream(it)
            }
        }
    }
    
    @Test
    fun `test unsupported audio file`() {
        assertFails {
            SoundProvider.getAudioInputStream(UNSUPPORTED_FILE)
        }
    }
    
    @Test
    fun `test unsupported audio input stream`() {
        assertFails {
            Files.newInputStream(UNSUPPORTED_FILE.toPath()).use {
                SoundProvider.getAudioInputStream(it)
            }
        }
    }
    
    @Test
    fun `test supported audio file format from file`() {
        for (file in SUPPORTED_FILES) {
            try {
                SoundProvider.getAudioFileFormat(file)
            } catch (_: Throwable) {
                fail("Unsupported file: $file")
            }
        }
    }
    
    @Test
    fun `test supported audio file format from input stream`() {
        for (file in SUPPORTED_FILES) {
            Files.newInputStream(file.toPath()).use {
                SoundProvider.getAudioFileFormat(it)
            }
        }
    }
    
    @Test
    fun `test unsupported audio file format`() {
        assertFails {
            SoundProvider.getAudioFileFormat(UNSUPPORTED_FILE)
        }
    }
    
    @Test
    fun `test unsupported audio file format from input stream`() {
        assertFails {
            Files.newInputStream(UNSUPPORTED_FILE.toPath()).use {
                SoundProvider.getAudioFileFormat(it)
            }
        }
    }
    
    @Test
    fun `test reduce sample rate supported`() {
        assertTrue {
            SoundProvider.isConversionSupported(
                    PCM_STANDARD.derive(sampleRate = 16000f, frameRate = 16000f),
                    PCM_STANDARD
            )
        }
    }
    
    @Test
    fun `test convert stereo 22khz mp3 to mono pcm 44khz`() {
        SoundProvider.withMonoWavFileStream(RESOURCES_PATH.resolve("demo_stereo.mp3")) {
            println(consumeStream(it))
        }
    }
    
    @Test
    fun `test mono mp3 44khz to mono pcm 44khz`() {
        SoundProvider.withMonoWavFileStream(RESOURCES_PATH.resolve("demo.mp3")) {
            println(consumeStream(it))
        }
    }
    
    @Test
    fun `test idempotent conversion`() {
        SoundProvider.withMonoWavFileStream(RESOURCES_PATH.resolve("demo.wav")) {
            println(consumeStream(it))
        }
    }
    
    private fun consumeStream(inputStream: InputStream): Long {
        var sum = 0L
        var x = 0
        do {
            sum += x
            x = inputStream.read()
        } while (x >= 0)
        return sum
    }
    
    private fun AudioFormat.derive(
            encoding: AudioFormat.Encoding = this.encoding,
            sampleRate: Float = this.sampleRate,
            sampleSizeInBits: Int = this.sampleSizeInBits,
            channels: Int = this.channels,
            frameSize: Int = this.frameSize,
            frameRate: Float = this.frameRate,
            bigEndian: Boolean = this.isBigEndian
    ): AudioFormat {
        return AudioFormat(encoding, sampleRate, sampleSizeInBits, channels, frameSize, frameRate, bigEndian)
    }
    
    companion object {
        private val SUPPORTED_FILES = sequenceOf("demo.wav", "demo.mp3").map { RESOURCES_PATH.resolve(it).toFile() }
        private val UNSUPPORTED_FILE = RESOURCES_PATH.resolve("not_audio").toFile()
        private val PCM_STANDARD = AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                44100f,
                16,
                1,
                2,
                44100f,
                false
        )
    }
}