package dev.doggo.shared.contract

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class StrictDigestTest {

    @Test
    fun testNistSha256Vectors() {
        // NIST vector 1: Empty string
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            StrictSyncDigest.sha256Hex(ByteArray(0))
        )

        // NIST vector 2: "abc"
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            StrictSyncDigest.sha256Hex("abc".encodeToByteArray())
        )

        // NIST vector 3: 56-byte padding boundary string
        val boundary56 = "abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq"
        assertEquals(
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            StrictSyncDigest.sha256Hex(boundary56.encodeToByteArray())
        )
    }

    @Test
    fun testDeterministicDigestAndFixedVector() {
        val data = StrictTodoData(
            id = "f47ac10b-58cc-4372-a567-0e02b2c3d479",
            title = "Buy groceries",
            completed = false,
            createdAt = 1791840000000L
        )

        val digest1 = StrictSyncDigest.computeDigest(
            domain = "strict-v1",
            appId = "doggo",
            userId = "65a123456789abcdef012345",
            epoch = "550e8400-e29b-41d4-a716-446655440000",
            tableName = "todos",
            rowId = "f47ac10b-58cc-4372-a567-0e02b2c3d479",
            opId = "a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d",
            kind = StrictOperationKind.CREATE,
            expectedRevision = 0L,
            data = data
        )

        val digest2 = StrictSyncDigest.computeDigest(
            domain = "strict-v1",
            appId = "doggo",
            userId = "65a123456789abcdef012345",
            epoch = "550e8400-e29b-41d4-a716-446655440000",
            tableName = "todos",
            rowId = "f47ac10b-58cc-4372-a567-0e02b2c3d479",
            opId = "a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d",
            kind = StrictOperationKind.CREATE,
            expectedRevision = 0L,
            data = data
        )

        assertEquals(digest1, digest2)
        assertEquals(64, digest1.length)
        assertEquals("69dfe7ff807736afcd95f303e97abc60a096d25a8bffcfbd99592b77df4fb865", digest1)
    }

    @Test
    fun testDeleteFixedVectorAndEmptyData() {
        val deleteDigest = StrictSyncDigest.computeDigest(
            appId = "doggo",
            userId = "user-1",
            epoch = "epoch-1",
            tableName = "todos",
            rowId = "row-1",
            opId = "op-del",
            kind = StrictOperationKind.DELETE,
            expectedRevision = 5L,
            data = null
        )

        assertEquals("b2b618ddd0e6a8d5e186b582ff12fdb76e8be62ca30f1c0d5cc02df7cf615711", deleteDigest)
    }

    @Test
    fun testFieldChangesProduceDistinctDigests() {
        val baseData = StrictTodoData(
            id = "f47ac10b-58cc-4372-a567-0e02b2c3d479",
            title = "Walk the dog",
            completed = false,
            createdAt = 1791840000000L
        )

        fun digest(
            kind: StrictOperationKind = StrictOperationKind.UPDATE,
            expectedRevision: Long = 1L,
            data: StrictTodoData? = baseData
        ) = StrictSyncDigest.computeDigest(
            appId = "doggo",
            userId = "user-1",
            epoch = "epoch-1",
            tableName = "todos",
            rowId = "row-1",
            opId = "op-1",
            kind = kind,
            expectedRevision = expectedRevision,
            data = data
        )

        val d1 = digest()
        val d2 = digest(expectedRevision = 2L)
        val d3 = digest(kind = StrictOperationKind.DELETE, data = null)
        val d4 = digest(data = baseData.copy(completed = true))
        val d5 = digest(data = baseData.copy(title = "Walk two dogs"))

        assertNotEquals(d1, d2)
        assertNotEquals(d1, d3)
        assertNotEquals(d1, d4)
        assertNotEquals(d1, d5)
    }

    @Test
    fun testInvalidCombinationsRejected() {
        assertFailsWith<IllegalArgumentException> {
            StrictSyncDigest.computeDigest(
                appId = "doggo", userId = "u1", epoch = "e1", tableName = "todos", rowId = "r1", opId = "o1",
                kind = StrictOperationKind.CREATE, expectedRevision = 0L, data = null
            )
        }

        assertFailsWith<IllegalArgumentException> {
            StrictSyncDigest.computeDigest(
                appId = "doggo", userId = "u1", epoch = "e1", tableName = "todos", rowId = "r1", opId = "o1",
                kind = StrictOperationKind.DELETE, expectedRevision = 0L,
                data = StrictTodoData("r1", "title", false, 0L)
            )
        }

        assertFailsWith<IllegalArgumentException> {
            StrictSyncDigest.computeDigest(
                appId = "doggo", userId = "u1", epoch = "e1", tableName = "todos", rowId = "r1", opId = "o1",
                kind = StrictOperationKind.CREATE, expectedRevision = -1L,
                data = StrictTodoData("r1", "title", false, 0L)
            )
        }
    }
}
