package dev.tabbyrush.shared.contract

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RemoteContractVectorTest {

    private val json = Json { ignoreUnknownKeys = false; isLenient = false }

    @Test
    fun testSerializationRoundTrip() {
        val item = RemoteInboxItem(
            id = "baf6dc3e-3099-4ae0-8767-985552ef0b82",
            appId = "tabby-rush",
            title = "Maintenance notice",
            body = "Please save your work before maintenance.",
            createdAt = "2026-10-18T00:12:00.000Z",
            expiresAt = "2027-01-16T00:12:00.000Z",
            readAt = null
        )
        val serializedItem = json.encodeToString(RemoteInboxItem.serializer(), item)
        val deserializedItem = json.decodeFromString(RemoteInboxItem.serializer(), serializedItem)
        assertEquals(item, deserializedItem)

        val page = RemoteInboxPage(items = listOf(item), nextCursor = null, serverNow = "2026-10-18T00:12:02.000Z")
        val serializedPage = json.encodeToString(RemoteInboxPage.serializer(), page)
        val deserializedPage = json.decodeFromString(RemoteInboxPage.serializer(), serializedPage)
        assertEquals(page, deserializedPage)

        val unread = RemoteUnreadResponse(unreadCount = 1, serverNow = "2026-10-18T00:12:02.000Z")
        val serializedUnread = json.encodeToString(RemoteUnreadResponse.serializer(), unread)
        assertEquals(unread, json.decodeFromString(RemoteUnreadResponse.serializer(), serializedUnread))

        val readResult = RemoteReadResult(
            id = item.id,
            readAt = "2026-10-18T00:12:05.000Z",
            unreadCount = 0,
            serverNow = "2026-10-18T00:12:05.000Z"
        )
        val serializedRead = json.encodeToString(RemoteReadResult.serializer(), readResult)
        assertEquals(readResult, json.decodeFromString(RemoteReadResult.serializer(), serializedRead))

        val err = RemoteErrorEnvelope(
            error = RemoteErrorDetail(
                code = "audienceChanged",
                message = "Recipient eligibility changed; preview again.",
                retryable = false,
                requestId = "a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d",
                details = kotlinx.serialization.json.buildJsonObject {}
            )
        )
        val serializedErr = json.encodeToString(RemoteErrorEnvelope.serializer(), err)
        assertEquals(err, json.decodeFromString(RemoteErrorEnvelope.serializer(), serializedErr))
    }

    @Test
    fun testWireFixtureDecoding() {
        val wireInboxJson = """{"items":[{"id":"baf6dc3e-3099-4ae0-8767-985552ef0b82","appId":"tabby-rush","title":"Maintenance notice","body":"Please save your work before maintenance.","createdAt":"2026-10-18T00:12:00.000Z","expiresAt":"2027-01-16T00:12:00.000Z","readAt":null}],"nextCursor":null,"serverNow":"2026-10-18T00:12:02.000Z"}"""
        val page = json.decodeFromString(RemoteInboxPage.serializer(), wireInboxJson)
        assertEquals(1, page.items.size)
        assertEquals("baf6dc3e-3099-4ae0-8767-985552ef0b82", page.items[0].id)
        assertEquals("tabby-rush", page.items[0].appId)
        assertEquals("2026-10-18T00:12:02.000Z", page.serverNow)

        val wireErrorJson = """{"error":{"code":"audienceChanged","message":"Recipient eligibility changed; preview again.","retryable":false,"requestId":"a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d","details":{}}}"""
        val errEnv = json.decodeFromString(RemoteErrorEnvelope.serializer(), wireErrorJson)
        assertEquals("audienceChanged", errEnv.error.code)
        assertFalse(errEnv.error.retryable)
        assertEquals("a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d", errEnv.error.requestId)
        assertTrue(errEnv.error.details != null)
    }

    @Test
    fun testCursorWireVector() {
        val wireCursor = "eyJ2IjoxLCJraW5kIjoiaW5ib3giLCJhcHBJZCI6InRhYmJ5LXJ1c2giLCJ1c2VyU2NvcGUiOiI1MDdmMWY3N2JjZjg2Y2Q3OTk0MzkwMTEiLCJ1cHBlclRpbWUiOiIyMDI2LTEwLTE4VDAwOjEyOjAyLjAwMFoiLCJ1cHBlcklkIjoiZmZmZmZmZmYtZmZmZi1mZmZmLWZmZmYtZmZmZmZmZmZmZmZmIiwiYWZ0ZXJUaW1lIjoiMjAyNi0xMC0xOFQwMDoxMjowMC4wMDBaIiwiYWZ0ZXJJZCI6ImJhZjZkYzNlLTMwOTktNGFlMC04NzY3LTk4NTU1MmVmMGI4MiJ9.7v-D756qJk1mZr54UniJ011LwVUahz3vxkOuvKW6H6Y"
        val parts = wireCursor.split(".")
        assertEquals(2, parts.size)
        val payloadBytes = base64UrlDecode(parts[0])
        val payloadJsonStr = payloadBytes.decodeToString()
        assertTrue(payloadJsonStr.contains("\"kind\":\"inbox\""))
        assertTrue(payloadJsonStr.contains("\"appId\":\"tabby-rush\""))
        assertTrue(payloadJsonStr.contains("\"userScope\":\"507f1f77bcf86cd799439011\""))
    }
    @Test
    fun testUuidV5GoldenVectors() {
        assertEquals(
            "5e31fb87-f826-579e-b208-2a308490db90",
            computeUuidV5("b29b36f3-1c6d-5a55-94c0-045e243c2e4d", "[\"tabby-rush\",\"baf6dc3e-3099-4ae0-8767-985552ef0b82\",\"507f1f77bcf86cd799439011\"]")
        )
        assertEquals(
            "3290bfa2-b839-5764-bef6-fa0680c1b8a5",
            computeUuidV5("0e9bf528-13dc-58b4-824e-fdd139f77d5c", "[\"baf6dc3e-3099-4ae0-8767-985552ef0b82\",\"507f1f77bcf86cd799439011\",\"sse\",null,null]")
        )
        assertEquals(
            "de3e2967-6ebc-5d96-aa4f-e64f3c368b63",
            computeUuidV5("0e9bf528-13dc-58b4-824e-fdd139f77d5c", "[\"baf6dc3e-3099-4ae0-8767-985552ef0b82\",\"507f1f77bcf86cd799439011\",\"fcm\",\"a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d\",\"17\"]")
        )
        assertEquals(
            "1972dac7-d18d-5b7a-88f2-e4779a459c06",
            computeUuidV5("f37cd6fb-f90b-5e34-b065-c2be584e0c61", "[\"baf6dc3e-3099-4ae0-8767-985552ef0b82\",\"507f1f77bcf86cd799439011\",0]")
        )
    }

    @Test
    fun testDigestGoldenVectors() {
        val audSelected = "{\"appId\":\"tabby-rush\",\"audience\":{\"mode\":\"selected\",\"userIds\":[\"507f1f77bcf86cd799439011\"]},\"recipientIds\":[\"507f1f77bcf86cd799439011\"]}"
        assertEquals("sha256:3b79fbc0c71a21c9597c12864921255705fb2a2bb190c5e4e66b17e4740daf70", "sha256:" + Sha256.sha256Hex(audSelected.encodeToByteArray()))

        val audAll = "{\"appId\":\"tabby-rush\",\"audience\":{\"mode\":\"all\"},\"recipientIds\":[\"507f1f77bcf86cd799439011\",\"507f1f77bcf86cd799439012\"]}"
        assertEquals("sha256:78c0cce67bac4168cad5cc5071473f88b3383ae0b0e863fb86c76a1c4d76b636", "sha256:" + Sha256.sha256Hex(audAll.encodeToByteArray()))

        val audAdmins = "{\"appId\":\"tabby-rush\",\"audience\":{\"mode\":\"admins\"},\"recipientIds\":[\"507f1f77bcf86cd799439011\"]}"
        assertEquals("sha256:eb52954adb601beacabaebc2c39fd00951fcdf7e0590b563c0dd6e529c043080", "sha256:" + Sha256.sha256Hex(audAdmins.encodeToByteArray()))

        val reqAscii = "{\"previewId\":\"a1c8249a-6f8d-4e73-97f5-dcebd10b407b\",\"audienceDigest\":\"sha256:3b79fbc0c71a21c9597c12864921255705fb2a2bb190c5e4e66b17e4740daf70\",\"title\":\"Maintenance notice\",\"body\":\"Please save your work before maintenance.\"}"
        assertEquals("sha256:f31a1a62edf5374b573e495c92c22142db391357c8de0ec9c12965f6a5059d47", "sha256:" + Sha256.sha256Hex(reqAscii.encodeToByteArray()))

        val reqUnicode = "{\"previewId\":\"a1c8249a-6f8d-4e73-97f5-dcebd10b407b\",\"audienceDigest\":\"sha256:3b79fbc0c71a21c9597c12864921255705fb2a2bb190c5e4e66b17e4740daf70\",\"title\":\"Thông báo bảo trì: TabbyRush\",\"body\":\"Vui lòng lưu công việc trước khi hệ thống bảo trì lúc 00:00 UTC.\\n\\\"TabbyRush\\\" \\\\ 🐱\"}"
        assertEquals("sha256:6a6d725093346aca0dd5a46536e0bc7de8ba585a2564e6534954cb537ded233a", "sha256:" + Sha256.sha256Hex(reqUnicode.encodeToByteArray()))

        val bindOp = "{\"appId\":\"tabby-rush\",\"installId\":\"a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d\",\"operationKind\":\"bind\",\"operationGeneration\":\"17\",\"ownerId\":\"507f1f77bcf86cd799439011\",\"platform\":\"android\",\"tokenHash\":\"bf243062400fdb658ea1b2536dc1244dfe259314d79bfa46da8f38ff8554f74f\",\"appVersion\":\"1.0.0\",\"expectedBindingGeneration\":null}"
        assertEquals("4a9ae79e2b9095572381bac54e47ac1e75cccaf0facb195d42ce55fba7074cca", Sha256.sha256Hex(bindOp.encodeToByteArray()))

        val unbindOp = "{\"appId\":\"tabby-rush\",\"installId\":\"a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d\",\"operationKind\":\"unbind\",\"operationGeneration\":\"18\",\"ownerId\":\"507f1f77bcf86cd799439011\",\"platform\":null,\"tokenHash\":null,\"appVersion\":null,\"expectedBindingGeneration\":\"17\"}"
        assertEquals("d9a378d326c1145dfa46aa6ad9b4c4dcc52dd0263fac1b1518c94f177c11c077", Sha256.sha256Hex(unbindOp.encodeToByteArray()))
    }

    @Test
    fun testNormalizationVectors() {
        val title1 = "   System Notice \t "
        assertEquals("System Notice", title1.trim())

        val body1 = " \r\nLine 1\r\nLine 2\r\nLine 3\r\n "
        assertEquals("Line 1\nLine 2\nLine 3", body1.replace("\r\n", "\n").replace("\r", "\n").trim())

        val body2 = "\tFirst paragraph.\r\n\r\nSecond paragraph.\n\n"
        assertEquals("First paragraph.\n\nSecond paragraph.", body2.replace("\r\n", "\n").replace("\r", "\n").trim())
    }

    @Test
    fun testNumericGenerationComparison() {
        assertTrue(compareGenerations("10", "9") > 0, "'10' must be numerically greater than '9'")
        assertTrue(compareGenerations("18", "17") > 0, "'18' must be numerically greater than '17'")
        assertTrue(compareGenerations("100", "99") > 0, "'100' must be numerically greater than '99'")
        assertTrue(compareGenerations("9223372036854775807", "9223372036854775806") > 0)
        assertEquals(0, compareGenerations("42", "42"))
        assertTrue(compareGenerations("9", "10") < 0)
    }

    @Test
    fun testLimitsValidation() {
        // Title: 1..200 Unicode scalars, <= 800 UTF-8 bytes
        assertTrue(validateTitle("Valid title"))
        assertFalse(validateTitle(""), "Empty title is rejected")
        val longTitle = "a".repeat(201)
        assertFalse(validateTitle(longTitle), "Title > 200 scalars is rejected")

        // Body: 1..4000 Unicode scalars, <= 16384 UTF-8 bytes
        assertTrue(validateBody("Valid body"))
        assertFalse(validateBody(""), "Empty body is rejected")
        val longBody = "b".repeat(4001)
        assertFalse(validateBody(longBody), "Body > 4000 scalars is rejected")

        // Audience: 1..500
        assertTrue(validateAudienceCount(1))
        assertTrue(validateAudienceCount(500))
        assertFalse(validateAudienceCount(0), "Empty audience rejected")
        assertFalse(validateAudienceCount(501), "Audience > 500 overflow rejected")
    }

    private fun compareGenerations(a: String, b: String): Int {
        val numA = a.toULongOrNull() ?: error("Invalid generation: $a")
        val numB = b.toULongOrNull() ?: error("Invalid generation: $b")
        return numA.compareTo(numB)
    }

    private fun validateTitle(title: String): Boolean {
        val trimmed = title.trim()
        val scalarCount = trimmed.codePointCount(0, trimmed.length)
        val utf8Bytes = trimmed.encodeToByteArray().size
        return scalarCount in 1..200 && utf8Bytes <= 800 && !trimmed.contains('\u0000')
    }

    private fun validateBody(body: String): Boolean {
        val normalized = body.replace("\r\n", "\n").replace("\r", "\n").trim()
        val scalarCount = normalized.codePointCount(0, normalized.length)
        val utf8Bytes = normalized.encodeToByteArray().size
        return scalarCount in 1..4000 && utf8Bytes <= 16384 && !normalized.contains('\u0000')
    }

    private fun validateAudienceCount(count: Int): Boolean = count in 1..500

    private fun computeUuidV5(namespaceUuid: String, name: String): String {
        val nsHex = namespaceUuid.replace("-", "")
        val nsBytes = ByteArray(16) { i -> nsHex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
        val nameBytes = name.encodeToByteArray()
        val combined = ByteArray(nsBytes.size + nameBytes.size)
        nsBytes.copyInto(combined, 0)
        nameBytes.copyInto(combined, nsBytes.size)
        val hash = sha1Bytes(combined)
        val raw = hash.copyOf(16)
        raw[6] = ((raw[6].toInt() and 0x0F) or 0x50).toByte()
        raw[8] = ((raw[8].toInt() and 0x3F) or 0x80).toByte()
        val sb = StringBuilder(36)
        for (i in 0 until 16) {
            if (i == 4 || i == 6 || i == 8 || i == 10) sb.append('-')
            val v = raw[i].toInt() and 0xFF
            if (v < 16) sb.append('0')
            sb.append(v.toString(16))
        }
        return sb.toString()
    }

    private fun sha1Bytes(bytes: ByteArray): ByteArray {
        var h0 = 0x67452301; var h1 = 0xEFCDAB89.toInt(); var h2 = 0x98BADCFE.toInt()
        var h3 = 0x10325476; var h4 = 0xC3D2E1F0.toInt()
        val bitLen = bytes.size.toLong() * 8L
        val padLen = ((56 - ((bytes.size + 1) % 64)) + 64) % 64
        val totalLen = bytes.size + 1 + padLen + 8
        val padded = ByteArray(totalLen)
        bytes.copyInto(padded, 0)
        padded[bytes.size] = 0x80.toByte()
        for (i in 7 downTo 0) padded[totalLen - 8 + (7 - i)] = ((bitLen ushr (i * 8)) and 0xFFL).toByte()
        val w = IntArray(80)
        for (b in 0 until (totalLen / 64)) {
            val off = b * 64
            for (i in 0 until 16) {
                w[i] = ((padded[off + i * 4].toInt() and 0xFF) shl 24) or
                       ((padded[off + i * 4 + 1].toInt() and 0xFF) shl 16) or
                       ((padded[off + i * 4 + 2].toInt() and 0xFF) shl 8) or
                       (padded[off + i * 4 + 3].toInt() and 0xFF)
            }
            for (i in 16 until 80) {
                val v = w[i - 3] xor w[i - 8] xor w[i - 14] xor w[i - 16]
                w[i] = (v shl 1) or (v ushr 31)
            }
            var a = h0; var c = h1; var d = h2; var e = h3; var f = h4
            for (i in 0 until 80) {
                val (fn, k) = when (i) {
                    in 0..19 -> ((c and d) or (c.inv() and e)) to 0x5A827999
                    in 20..39 -> (c xor d xor e) to 0x6ED9EBA1
                    in 40..59 -> ((c and d) or (c and e) or (d and e)) to 0x8F1BBCDC.toInt()
                    else -> (c xor d xor e) to 0xCA62C1D6.toInt()
                }
                val temp = ((a shl 5) or (a ushr 27)) + fn + f + k + w[i]
                f = e; e = d; d = (c shl 30) or (c ushr 2); c = a; a = temp
            }
            h0 += a; h1 += c; h2 += d; h3 += e; h4 += f
        }
        val res = ByteArray(20)
        for (i in 0..3) res[i] = (h0 ushr (24 - i * 8)).toByte()
        for (i in 0..3) res[4 + i] = (h1 ushr (24 - i * 8)).toByte()
        for (i in 0..3) res[8 + i] = (h2 ushr (24 - i * 8)).toByte()
        for (i in 0..3) res[12 + i] = (h3 ushr (24 - i * 8)).toByte()
        for (i in 0..3) res[16 + i] = (h4 ushr (24 - i * 8)).toByte()
        return res
    }

    private fun base64UrlDecode(str: String): ByteArray {
        return java.util.Base64.getUrlDecoder().decode(str)
    }
}
