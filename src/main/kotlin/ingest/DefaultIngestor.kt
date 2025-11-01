package main.kotlin.ingest

import java.io.ByteArrayInputStream
import java.security.MessageDigest

/**
 * Default implementation of Ingestor that:
 * - Reads the source once to compute detectedMime, size, sha256
 * - Validates against contentLength, maxContentLength, acceptedMimes
 * - Forwards the buffered bytes to the sink
 */
class DefaultIngestor : Ingestor {

    override fun ingest(meta: UploadMeta, cfg: IngestConfig, src: ByteSource, sink: IngestSink) {
        val errors = mutableListOf<String>()

        // Buffer to store all bytes for re-forwarding to sink
        val allBytes = mutableListOf<ByteArray>()
        var totalSize = 0L
        val md = MessageDigest.getInstance("SHA-256")

        // Read all bytes once
        src.use { source ->
            while (true) {
                val chunk = source.nextChunk() ?: break

                // Check if we're exceeding maxContentLength early
                totalSize += chunk.size
                if (totalSize > cfg.maxContentLength) {
                    errors.add("maxContentLength exceeded: $totalSize > ${cfg.maxContentLength}")
                    // Continue reading to completion but track the error
                }

                allBytes.add(chunk)
                md.update(chunk)
            }
        }

        // Compute SHA-256
        val sha256 = md.digest().joinToString("") { b -> "%02x".format(b) }

        // Detect MIME type
        val detectedMime = detectMimeType(allBytes)

        // Validate contentLength if provided
        if (meta.contentLength != null && meta.contentLength != totalSize) {
            errors.add("contentLength mismatch: declared ${meta.contentLength}, actual $totalSize")
        }

        // Validate maxContentLength (already tracked during reading)
        if (totalSize > cfg.maxContentLength && errors.none { it.contains("maxContentLength") }) {
            errors.add("maxContentLength exceeded: $totalSize > ${cfg.maxContentLength}")
        }

        // Validate accepted MIME types
        if (detectedMime !in cfg.acceptedMimes) {
            errors.add("mime not accepted: $detectedMime not in ${cfg.acceptedMimes}")
        }

        // Check for claimedMime mismatch (warning, not a blocker)
        if (meta.claimedMime != detectedMime) {
            errors.add("claimedMime mismatch: claimed '${meta.claimedMime}', detected '$detectedMime'")
        }

        val result = IngestResult(
            detectedMime = detectedMime,
            size = totalSize,
            sha256 = sha256,
            ok = errors.isEmpty() || (errors.size == 1 && errors[0].startsWith("claimedMime mismatch")),
            errors = errors
        )

        // Forward to sink with buffered bytes as a new ByteSource
        val bufferedSource = BufferedByteSource(allBytes)
        sink.persist(meta, result, bufferedSource)
    }

    private fun detectMimeType(chunks: List<ByteArray>): String {
        if (chunks.isEmpty()) {
            return "application/octet-stream"
        }

        val firstChunk = chunks[0]
        if (firstChunk.isEmpty()) {
            return "application/octet-stream"
        }

        // PDF: starts with %PDF
        if (firstChunk.size >= 4 &&
            firstChunk[0] == 0x25.toByte() && // %
            firstChunk[1] == 0x50.toByte() && // P
            firstChunk[2] == 0x44.toByte() && // D
            firstChunk[3] == 0x46.toByte()) { // F
            return "application/pdf"
        }

        // JPEG: starts with FF D8
        if (firstChunk.size >= 2 &&
            firstChunk[0] == 0xFF.toByte() && // FF
            firstChunk[1] == 0xD8.toByte()) { // D8
            return "image/jpeg"
        }

        // PNG: starts with 89 50 4E 47 0D 0A 1A 0A
        if (firstChunk.size >= 8 &&
            firstChunk[0] == 0x89.toByte() &&
            firstChunk[1] == 0x50.toByte() &&
            firstChunk[2] == 0x4E.toByte() &&
            firstChunk[3] == 0x47.toByte() &&
            firstChunk[4] == 0x0D.toByte() &&
            firstChunk[5] == 0x0A.toByte() &&
            firstChunk[6] == 0x1A.toByte() &&
            firstChunk[7] == 0x0A.toByte()) {
            return "image/png"
        }

        // DOCX/ZIP: starts with PK (50 4B)
        if (firstChunk.size >= 2 &&
            firstChunk[0] == 0x50.toByte() && // P
            firstChunk[1] == 0x4B.toByte()) { // K
            // Need to check for DOCX-specific content
            // DOCX files contain [Content_Types].xml
            val allBytesFlat = chunks.flatMap { it.asIterable() }.toByteArray()
            val asString = String(allBytesFlat, Charsets.ISO_8859_1)
            if (asString.contains("word/") || asString.contains("wordprocessingml")) {
                return "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            }
            return "application/zip"
        }

        return "application/octet-stream"
    }

    /**
     * ByteSource that replays buffered chunks
     */
    private class BufferedByteSource(private val chunks: List<ByteArray>) : ByteSource {
        private var index = 0

        override fun nextChunk(): ByteArray? {
            return if (index < chunks.size) {
                chunks[index++]
            } else {
                null
            }
        }

        override fun close() {
            // Nothing to close, already buffered in memory
        }
    }
}
