package dev.doggo.desktop

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DotEnvLoaderTest {

    @Test
    fun testMissingFileReturnsEmptyMap() {
        val nonExistent = File("build/non-existent-${System.currentTimeMillis()}.env")
        val result = DotEnvLoader.parse(nonExistent)
        assertTrue(result.isEmpty())
    }

    @Test
    fun testParseValidDotEnv() {
        val tempFile = File.createTempFile("test-dotenv", ".env")
        tempFile.deleteOnExit()
        tempFile.writeText(
            """
            # This is a comment
            
            APPROVED_HUB_ORIGIN=https://hub.example.com
            DOGGO_API_KEY="my-secret-key-123"
            SINGLE_QUOTED='single-quoted-val'
            VALUE_WITH_EQUALS=foo=bar=baz
            SPACED_KEY = spaced_value 
            
            # Another comment
            EMPTY_VAL=
            """.trimIndent().replace("\n", "\r\n") // test CRLF
        )

        val result = DotEnvLoader.parse(tempFile)
        assertEquals("https://hub.example.com", result["APPROVED_HUB_ORIGIN"])
        assertEquals("my-secret-key-123", result["DOGGO_API_KEY"])
        assertEquals("single-quoted-val", result["SINGLE_QUOTED"])
        assertEquals("foo=bar=baz", result["VALUE_WITH_EQUALS"])
        assertEquals("spaced_value", result["SPACED_KEY"])
        assertEquals("", result["EMPTY_VAL"])
    }

    @Test
    fun testLoadWithExplicitFile() {
        val tempFile = File.createTempFile("custom-env", ".env")
        tempFile.deleteOnExit()
        tempFile.writeText("CUSTOM_KEY=custom_value\n")

        val result = DotEnvLoader.load(customFile = tempFile)
        assertEquals("custom_value", result["CUSTOM_KEY"])
    }

    @Test
    fun testLoadWithSystemProperty() {
        val tempFile = File.createTempFile("sys-prop-env", ".env")
        tempFile.deleteOnExit()
        tempFile.writeText("SYS_KEY=sys_value\n")

        val originalProp = System.getProperty("doggo.env.file")
        try {
            System.setProperty("doggo.env.file", tempFile.absolutePath)
            val result = DotEnvLoader.load()
            assertEquals("sys_value", result["SYS_KEY"])
        } finally {
            if (originalProp != null) {
                System.setProperty("doggo.env.file", originalProp)
            } else {
                System.clearProperty("doggo.env.file")
            }
        }
    }

    @Test
    fun testParseMalformedQuotesDoesNotCrashOrDropSubsequentLines() {
        val tempFile = File.createTempFile("malformed-env", ".env")
        tempFile.deleteOnExit()
        tempFile.writeText(
            """
            LONE_QUOTE='
            LONE_DOUBLE="
            SUBSEQUENT_KEY=valid_value
            """.trimIndent()
        )

        val result = DotEnvLoader.parse(tempFile)
        assertEquals("'", result["LONE_QUOTE"])
        assertEquals("\"", result["LONE_DOUBLE"])
        assertEquals("valid_value", result["SUBSEQUENT_KEY"])
    }
}
