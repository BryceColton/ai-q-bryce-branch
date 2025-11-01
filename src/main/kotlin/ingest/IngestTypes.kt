package main.kotlin.ingest

import java.io.Closeable
import java.io.FileInputStream
import java.io.InputStream
import java.nio.file.Path

/** Metadata provided by the uploader. */
data class UploadMeta(
    val filename: String,
    val claimedMime: String,
    val contentLength: Long?
)

/** Static ingest configuration. */
data class IngestConfig(
    val maxContentLength: Long,
    val acceptedMimes: Set<String>
)

/** Result of ingest validation and content analysis. */
data class IngestResult(
    val detectedMime: String,
    val size: Long,
    val sha256: String,
    val ok: Boolean,
    val errors: List<String>
)

/** Finite, read-once sequence of bytes. */
interface ByteSource : Closeable {
    /** Returns next non-empty chunk or null at EOF. */
    fun nextChunk(): ByteArray?
    override fun close()
}

/** ByteSource over an InputStream (non-rewindable). */
class InputStreamByteSource(
    private val input: InputStream,
    private val chunkSize: Int = DEFAULT_CHUNK_SIZE
) : ByteSource {
    private var closed = false

    override fun nextChunk(): ByteArray? {
        if (closed) return null
        val buf = ByteArray(chunkSize)
        val read = input.read(buf)
        return when {
            read < 0 -> null
            read == 0 -> nextChunk() // try again to get actual bytes
            read == buf.size -> buf
            else -> buf.copyOf(read)
        }
    }

    override fun close() {
        if (!closed) {
            closed = true
            input.close()
        }
    }

    companion object {
        const val DEFAULT_CHUNK_SIZE = 8192
    }
}

/** ByteSource over a file on disk; starts from beginning. */
class FileByteSource(
    private val path: Path,
    private val chunkSize: Int = InputStreamByteSource.DEFAULT_CHUNK_SIZE
) : ByteSource {
    private var inStream: InputStream? = null

    private fun ensureOpen(): InputStream {
        var s = inStream
        if (s == null) {
            s = FileInputStream(path.toFile())
            inStream = s
        }
        return s
    }

    override fun nextChunk(): ByteArray? {
        val s = ensureOpen()
        val buf = ByteArray(chunkSize)
        val read = s.read(buf)
        return when {
            read < 0 -> null
            read == 0 -> nextChunk()
            read == buf.size -> buf
            else -> buf.copyOf(read)
        }
    }

    override fun close() {
        inStream?.close()
        inStream = null
    }
}

/** The sink receives validated upload and result for downstream processing. */
interface IngestSink {
    fun persist(meta: UploadMeta, result: IngestResult, data: ByteSource)
}

/** Ingest component: validates and forwards to sink. */
interface Ingestor {
    fun ingest(meta: UploadMeta, cfg: IngestConfig, src: ByteSource, sink: IngestSink)
}
