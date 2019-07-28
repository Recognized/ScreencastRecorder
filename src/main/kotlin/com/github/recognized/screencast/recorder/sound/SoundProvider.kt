package com.github.recognized.screencast.recorder.sound

import com.github.recognized.kotlin.ranges.extensions.floorToInt
import com.intellij.openapi.diagnostic.logger
import javazoom.spi.mpeg.sampled.convert.MpegFormatConversionProvider
import javazoom.spi.mpeg.sampled.file.MpegAudioFileReader
import java.io.*
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import java.util.function.Supplier
import javax.sound.sampled.*
import kotlin.concurrent.thread
import kotlin.math.min

/** This object duplicates some part of [javax.sound.sampled.AudioSystem].
 *
 * It exists because of some unknown reasons in Intellij Platform and bad SPI design,
 * which does not allow to manually load implementation classes from code,
 * but only to load them from reading specially formatted files in META-INF/services directory.
 */
object SoundProvider {
    private val LOG = logger<SoundProvider>()
    private val MPEG_PROVIDER = MpegFormatConversionProvider()
    private val MPEG_FILE_READER = MpegAudioFileReader()
    
    @Throws(java.io.IOException::class, UnsupportedAudioFileException::class)
    fun getAudioInputStream(file: File): AudioInputStream {
        return try {
            AudioSystem.getAudioInputStream(file)
        } catch (ex: UnsupportedAudioFileException) {
            MPEG_FILE_READER.getAudioInputStream(file)
        }
    }
    
    @Throws(java.io.IOException::class, UnsupportedAudioFileException::class)
    fun getAudioInputStream(inputStream: InputStream): AudioInputStream {
        val stream = ExactSkippingBIS(inputStream)
        return try {
            AudioSystem.getAudioInputStream(stream)
        } catch (ex: UnsupportedAudioFileException) {
            MPEG_FILE_READER.getAudioInputStream(stream)
        }
    }
    
    fun isConversionSupported(targetFormat: AudioFormat, sourceFormat: AudioFormat): Boolean {
        return AudioSystem.isConversionSupported(targetFormat, sourceFormat)
                || MPEG_PROVIDER.isConversionSupported(targetFormat, sourceFormat)
    }
    
    /**
     * @throws IllegalArgumentException if conversion is not supported.
     */
    @Throws(IllegalArgumentException::class)
    fun getAudioInputStream(targetFormat: AudioFormat, stream: AudioInputStream): AudioInputStream {
        return if (MPEG_PROVIDER.isConversionSupported(targetFormat, stream.format)) {
            MPEG_PROVIDER.getAudioInputStream(targetFormat, stream)
        } else AudioSystem.getAudioInputStream(targetFormat, stream)
    }
    
    @Throws(java.io.IOException::class, UnsupportedAudioFileException::class)
    fun getAudioFileFormat(file: File): AudioFileFormat {
        return try {
            AudioSystem.getAudioFileFormat(file)
        } catch (ex: UnsupportedAudioFileException) {
            MPEG_FILE_READER.getAudioFileFormat(file)
        }
    }
    
    @Throws(java.io.IOException::class, UnsupportedAudioFileException::class)
    fun getAudioFileFormat(inputStream: InputStream): AudioFileFormat {
        val stream = ExactSkippingBIS(inputStream)
        return try {
            AudioSystem.getAudioFileFormat(stream)
        } catch (ex: UnsupportedAudioFileException) {
            MPEG_FILE_READER.getAudioFileFormat(stream)
        }
    }
    
    fun getAudioInputStream(
            rawData: InputStream,
            format: AudioFormat,
            length: Long
    ): AudioInputStream {
        return AudioInputStream(ExactSkippingBIS(rawData), format, length)
    }
    
    fun <T> withMonoWavFileStream(path: Path, block: (InputStream) -> T): T {
        return withMonoWavFileStream(Supplier { Files.newInputStream(path) }, block)
    }
    
    fun <T> withMonoWavFileStream(supplier: Supplier<InputStream>, block: (InputStream) -> T): T {
        val (stream, lock) = convertAudioLazy(supplier)
        LOG.info("Audio is converting.")
        stream.use {
            try {
                return block(it)
            } finally {
                lock.unlock()
                LOG.info("Audio converted.")
            }
        }
    }
    
    fun countFrames(inputStream: InputStream, audioFormat: AudioFormat): Long {
        var bytes = 0L
        inputStream.buffered().use {
            bytes = it.countBytes()
        }
        return bytes / audioFormat.frameSize
    }
    
    private fun AudioFormat.toMonoFormat(): AudioFormat {
        return AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                if (this.sampleRate > 0) this.sampleRate else 44100f,
                16,
                1,
                2,
                if (this.sampleRate > 0) this.sampleRate else 44100f,
                false
        )
    }
    
    private fun AudioFormat.toPcmPreservingChannels() =
            AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    44100f,
                    16,
                    channels,
                    2 * channels,
                    44100f,
                    false
            )
    
    private fun AudioFormat.toPcm(sampleRate: Float) =
            AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    sampleRate,
                    16,
                    channels,
                    2 * channels,
                    sampleRate,
                    false
            )
    
    // This is needed to make possible converting audio to recognizable WAV format without temporary file storage
    // and without storing whole decoded file in RAM, because it can be very big.
    // Idea of this code is just to convert and send audio stream on the fly,
    // but there are several tricks are needed to implement it.
    private fun convertAudioLazy(supplier: Supplier<InputStream>): Pair<InputStream, Lock> {
        val pipeIn = PipedInputStream(1 shl 14)
        val pipeOut = PipedOutputStream(pipeIn)
        val keepAlive = ReentrantLock()
        keepAlive.lock()
        thread {
            // This is needed because of lack of transitive closure in audio system conversions.
            // We need to manually convert audio through intermediate formats.
            withSizedMonoPcmStream(supplier) { mono ->
                // Lets convert it using pipe
                // No need for join, because execution continue after everything is written to pipe and then thread ends.
                pipeOut.use {
                    AudioSystem.write(mono, AudioFileFormat.Type.WAVE, it)
                }
                LOG.info("Data is written to pipe.")
                keepAlive.lock()
            }
        }
        return BufferedInputStream(pipeIn) to keepAlive
    }
    
    private fun withSizedMonoPcmStream(supplier: Supplier<InputStream>, action: (AudioInputStream) -> Unit) {
        val length = countFrames(supplier)
        withMonoPcmStream(supplier.get()) {
            action(createSizedAudioStream(it, length))
        }
    }
    
    fun withSizedPcmStream(supplier: Supplier<InputStream>, action: (AudioInputStream) -> Unit) {
        val length = countFrames(supplier)
        withPcmStream(supplier.get()) {
            action(createSizedAudioStream(it, length))
        }
    }
    
    fun <T> withWaveformPcmStream(source: InputStream, sampleRate: Float = 44100f, action: (AudioInputStream) -> T): T {
        return getAudioInputStream(source).use { encoded ->
            when {
                encoded.format == encoded.format.toPcm(sampleRate) -> {
                    action(encoded)
                }
                else -> {
                    if (!isConversionSupported(encoded.format.toPcm(sampleRate), encoded.format)) {
                        throw UnsupportedAudioFileException("Audio is not supported.")
                    }
                    getAudioInputStream(encoded.format.toPcm(sampleRate), encoded).use(action)
                }
            }
        }
    }
    
    fun getWaveformPcmFormat(format: AudioFormat, sampleRate: Float = 44100f): AudioFormat {
        return format.toPcm(sampleRate)
    }
    
    fun skipFrames(inputStream: AudioInputStream, buffer: ByteArray, count: Long) {
        with(inputStream) {
            var bytesToSkip = count * format.frameSize
            while (bytesToSkip != 0L) {
                val skipped = read(buffer, 0, min(bytesToSkip.floorToInt(), buffer.size))
                bytesToSkip -= skipped
            }
        }
    }
    
    private fun withPcmStream(source: InputStream, action: (AudioInputStream) -> Unit) {
        getAudioInputStream(source).use { encoded ->
            when {
                encoded.format == encoded.format.toPcmPreservingChannels() -> {
                    action(encoded)
                }
                else -> {
                    getAudioInputStream(encoded.format.toPcmPreservingChannels(), encoded).use(action)
                }
            }
        }
    }
    
    private fun withMonoPcmStream(source: InputStream, action: (AudioInputStream) -> Unit) {
        getAudioInputStream(source).use { encoded ->
            when {
                encoded.format == encoded.format.toMonoFormat() -> {
                    action(encoded)
                }
                encoded.format == encoded.format.toPcmPreservingChannels() -> {
                    getAudioInputStream(encoded.format.toMonoFormat(), encoded).use(action)
                }
                else -> {
                    getAudioInputStream(encoded.format.toPcmPreservingChannels(), encoded).use {
                        getAudioInputStream(it.format.toMonoFormat(), it).use(action)
                    }
                }
            }
        }
    }
    
    private fun countFrames(supplier: Supplier<InputStream>): Long {
        var length = 0L
        supplier.get().use { inputStream ->
            withMonoPcmStream(inputStream) { mono ->
                length = mono.countBytes()
            }
        }
        return length / 2
    }
    
    private fun InputStream.countBytes(): Long {
        var length = 0L
        var x = 0
        val buffer = ByteArray(1 shl 14)
        while (x != -1) {
            x = read(buffer)
            if (x != -1) {
                length += x
            }
        }
        return length
    }
    
    // Pre-calculate size of WAV file in frames and then use it in this function
    private fun createSizedAudioStream(source: AudioInputStream, size: Long): AudioInputStream {
        if (source.frameLength > 0) return source
        return object : AudioInputStream(
                ByteArrayInputStream(ByteArray(0)), // fake arguments
                source.format.toPcmPreservingChannels(),
                size
        ) {
            override fun skip(n: Long) = source.skip(n)
            
            override fun getFrameLength(): Long = size
            
            override fun available(): Int = source.available()
            
            override fun reset() = source.reset()
            
            override fun close() = source.close()
            
            override fun mark(readlimit: Int) = source.mark(readlimit)
            
            override fun markSupported(): Boolean = source.markSupported()
            
            override fun read(): Int = source.read()
            
            override fun read(b: ByteArray?): Int = source.read(b)
            
            override fun read(b: ByteArray?, off: Int, len: Int): Int = source.read(b, off, len)
            
            override fun getFormat(): AudioFormat = source.format
        }
    }
    
    
    private class ExactSkippingBIS(stream: InputStream) : BufferedInputStream(stream) {
        override fun skip(n: Long): Long {
            var totalSkipped = 0L
            val skipBuffer = ByteArray(1 shl 14)
            while (totalSkipped < n) {
                val res = read(skipBuffer, 0, min(skipBuffer.size, (n - totalSkipped).floorToInt()))
                if (res < 0) {
                    break
                }
                totalSkipped += res
            }
            return totalSkipped
        }
    }
}