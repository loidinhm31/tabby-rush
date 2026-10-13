package dev.doggo.shared.contract

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AccountKeyTest {

    @Test
    fun testValidAccountKeyFormat() {
        val key = AccountKey.create("https://hub.example.com", "user-uuid-1234")
        assertEquals(64, key.value.length)
        assertTrue(key.value.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun testDeterministicAccountKey() {
        val k1 = AccountKey.create("https://hub.example.com", "user-1")
        val k2 = AccountKey.create("https://hub.example.com", "user-1")
        assertEquals(k1, k2)
    }

    @Test
    fun testDistinctKeysForDifferentInputs() {
        val k1 = AccountKey.create("https://hub.example.com", "user-1")
        val k2 = AccountKey.create("https://hub.example.com", "user-2")
        val k3 = AccountKey.create("https://hub.other.com", "user-1")

        assertNotEquals(k1, k2)
        assertNotEquals(k1, k3)
    }

    @Test
    fun testFramingPreventsBoundaryCollision() {
        // "ab" + "c" vs "a" + "bc"
        val k1 = AccountKey.create("ab", "c")
        val k2 = AccountKey.create("a", "bc")
        assertNotEquals(k1, k2)
    }

    @Test
    fun testInvalidFormatRejected() {
        assertFailsWith<IllegalArgumentException> {
            AccountKey("too-short")
        }
        assertFailsWith<IllegalArgumentException> {
            AccountKey("A".repeat(64)) // uppercase rejected
        }
    }
}
