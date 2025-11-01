package main.kotlin

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath

@SpringBootTest
@AutoConfigureMockMvc
class HelloControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `GET root returns running message`() {
        mockMvc.perform(get("/"))
            .andExpect(status().isOk)
            .andExpect(content().string("Tybera project is running!"))
    }

    @Test
    fun `GET test-upload returns IngestResult for sample pdf`() {
        mockMvc.perform(get("/test-upload"))
            .andExpect(status().isOk)
            .andExpect(content().contentType("application/json"))
            .andExpect(jsonPath("$.detectedMime").value("application/pdf"))
            .andExpect(jsonPath("$.ok").value(true))
            .andExpect(jsonPath("$.errors").isEmpty)
    }

    @Test
    fun `POST upload with valid PDF returns IngestResult`() {
        val pdfContent = "%PDF-1.4\n1 0 obj\n<<\n/Type /Catalog\n/Pages 2 0 R\n>>\nendobj\n2 0 obj\n<<\n/Type /Pages\n/Kids [3 0 R]\n/Count 1\n>>\nendobj\n3 0 obj\n<<\n/Type /Page\n/Parent 2 0 R\n/MediaBox [0 0 612 792]\n/Contents 4 0 R\n>>\nendobj\n4 0 obj\n<<\n/Length 44\n>>\nstream\nBT\n/F1 12 Tf\n100 700 Td\n(Hello World) Tj\nET\nendstream\nendobj\nxref\n0 5\n0000000000 65535 f \n0000000009 00000 n \n0000000058 00000 n \n0000000115 00000 n \n0000000200 00000 n \ntrailer\n<<\n/Size 5\n/Root 1 0 R\n>>\nstartxref\n284\n%%EOF".toByteArray()
        val mockFile = MockMultipartFile("file", "test.pdf", "application/pdf", pdfContent)

        mockMvc.perform(multipart("/upload").file(mockFile))
            .andExpect(status().isOk)
            .andExpect(content().contentType("application/json"))
            .andExpect(jsonPath("$.detectedMime").value("application/pdf"))
            .andExpect(jsonPath("$.ok").value(true))
    }

    @Test
    fun `POST upload with invalid file returns error`() {
        val invalidContent = "invalid content".toByteArray()
        val mockFile = MockMultipartFile("file", "test.txt", "text/plain", invalidContent)

        mockMvc.perform(multipart("/upload").file(mockFile))
            .andExpect(status().isOk)
            .andExpect(content().contentType("application/json"))
            .andExpect(jsonPath("$.detectedMime").value("application/octet-stream"))
            .andExpect(jsonPath("$.ok").value(false))
            .andExpect(jsonPath("$.errors").isNotEmpty)
    }
}
