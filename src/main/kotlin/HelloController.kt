package main.kotlin

import main.kotlin.ingest.*
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

@RestController
class HelloController {

    private val ingestor: Ingestor = DefaultIngestor()

    private val config = IngestConfig(
        maxContentLength = 10_000_000L,
        acceptedMimes = setOf(
            "application/pdf",
            "image/png",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        )
    )

    @GetMapping("/")
    fun hello(): String = "Tybera project is running!"

    @GetMapping("/test-upload")
    fun testUpload(): ResponseEntity<IngestResult> {
        // Load sample.pdf for testing
        val sampleFile = Paths.get("test", "resources", "sample.pdf").toFile()
        if (!sampleFile.exists()) {
            return ResponseEntity.badRequest().body(IngestResult("application/octet-stream", 0, "", false, listOf("Sample file not found")))
        }

        val meta = UploadMeta(
            filename = "sample.pdf",
            claimedMime = "application/pdf",
            contentLength = sampleFile.length()
        )

        val src = FileByteSource(Paths.get("test", "resources", "sample.pdf"))
        val sink = FileSink()

        ingestor.ingest(meta, config, src, sink)

        return ResponseEntity.ok(sink.result!!)
    }

    @PostMapping("/upload")
    fun upload(@RequestParam("file") file: MultipartFile): ResponseEntity<IngestResult> {
        val meta = UploadMeta(
            filename = file.originalFilename ?: "unknown",
            claimedMime = file.contentType ?: "application/octet-stream",
            contentLength = file.size.takeIf { it > 0 }
        )

        val src = InputStreamByteSource(file.inputStream)
        val sink = FileSink() // Simple sink that saves to temp dir

        ingestor.ingest(meta, config, src, sink)

        return ResponseEntity.ok(sink.result!!)
    }

    private class FileSink : IngestSink {
        var result: IngestResult? = null

        override fun persist(meta: UploadMeta, result: IngestResult, data: ByteSource) {
            this.result = result
            // Save to a temp file for demo
            val tempFile = Files.createTempFile("upload-", ".tmp")
            Files.newOutputStream(tempFile).use { output ->
                data.use { source ->
                    while (true) {
                        val chunk = source.nextChunk() ?: break
                        output.write(chunk)
                    }
                }
            }
            // Optionally log the file path
            println("File saved to: $tempFile")
        }
    }
}
