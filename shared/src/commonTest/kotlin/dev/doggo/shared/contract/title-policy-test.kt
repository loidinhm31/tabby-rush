package dev.doggo.shared.contract

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TitlePolicyTest {

    @Test
    fun testValidTitleWithWhitespaceTrimming() {
        val raw = " \t\r\n\u00A0 Clean room \u3000\u2000 "
        val res = TitlePolicy.normalizeAndValidate(raw)
        assertTrue(res is ApiResult.Success)
        assertEquals("Clean room", res.value)
    }

    @Test
    fun testScalarCountingWithAstralEmoji() {
        val raw = "Dog \uD83D\uDC36 and Cat \uD83D\uDC31"
        val res = TitlePolicy.normalizeAndValidate(raw)
        assertTrue(res is ApiResult.Success)
        assertEquals("Dog \uD83D\uDC36 and Cat \uD83D\uDC31", res.value)
    }

    @Test
    fun testLengthBounds200And201() {
        val exactly200 = "a".repeat(200)
        val res200 = TitlePolicy.normalizeAndValidate(exactly200)
        assertTrue(res200 is ApiResult.Success)
        assertEquals(200, res200.value.length)

        val exactly201 = "a".repeat(201)
        val res201 = TitlePolicy.normalizeAndValidate(exactly201)
        assertTrue(res201 is ApiResult.Failure)
        assertEquals("TITLE_TOO_LONG", res201.error.code)
    }

    @Test
    fun testEmptyAndWhitespaceOnlyRejected() {
        val emptyRes = TitlePolicy.normalizeAndValidate("")
        assertTrue(emptyRes is ApiResult.Failure)
        assertEquals("TITLE_EMPTY", emptyRes.error.code)

        val spaceRes = TitlePolicy.normalizeAndValidate("   \t\n \u00A0 ")
        assertTrue(spaceRes is ApiResult.Failure)
        assertEquals("TITLE_EMPTY", spaceRes.error.code)
    }

    @Test
    fun testMalformedSurrogatesRejected() {
        val unpairedHigh = "Bad \uD83D string"
        val res = TitlePolicy.normalizeAndValidate(unpairedHigh)
        assertTrue(res is ApiResult.Failure)
        assertEquals("INVALID_UNICODE", res.error.code)

        val unpairedLow = "Bad \uDC36 string"
        val res2 = TitlePolicy.normalizeAndValidate(unpairedLow)
        assertTrue(res2 is ApiResult.Failure)
        assertEquals("INVALID_UNICODE", res2.error.code)
    }

    @Test
    fun testInternalControlCharactersRejected() {
        val withNull = "Hello\u0000World"
        val resNull = TitlePolicy.normalizeAndValidate(withNull)
        assertTrue(resNull is ApiResult.Failure)
        assertEquals("INVALID_CONTROL_CHARACTER", resNull.error.code)

        val withDel = "Delete\u007FMe"
        val resDel = TitlePolicy.normalizeAndValidate(withDel)
        assertTrue(resDel is ApiResult.Failure)
        assertEquals("INVALID_CONTROL_CHARACTER", resDel.error.code)
    }
}
