package dev.tabbyrush.shared.contract

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EndpointPolicyTest {

    @Test
    fun testValidHttpsEndpointsInBothModes() {
        val raw = "https://hub.example.com"
        val debugRes = EndpointPolicy.validate(raw, BuildMode.DEBUG)
        val releaseRes = EndpointPolicy.validate(raw, BuildMode.RELEASE)

        assertTrue(debugRes is ApiResult.Success)
        assertEquals("https://hub.example.com", debugRes.value)
        assertTrue(releaseRes is ApiResult.Success)
        assertEquals("https://hub.example.com", releaseRes.value)
    }

    @Test
    fun testTrailingSlashStripped() {
        val raw = "https://hub.example.com/"
        val res = EndpointPolicy.validate(raw, BuildMode.RELEASE)
        assertTrue(res is ApiResult.Success)
        assertEquals("https://hub.example.com", res.value)
    }

    @Test
    fun testDebugPermittedHttpOrigins() {
        val allowed = listOf(
            "http://localhost:3000",
            "http://127.0.0.1:3000",
            "http://10.0.2.2:3000"
        )

        for (origin in allowed) {
            val res = EndpointPolicy.validate(origin, BuildMode.DEBUG)
            assertTrue(res is ApiResult.Success, "Expected success for $origin in DEBUG mode")
            assertEquals(origin, res.value)
        }
    }

    @Test
    fun testCleartextHttpForbiddenInRelease() {
        val res = EndpointPolicy.validate("http://hub.example.com", BuildMode.RELEASE)
        assertTrue(res is ApiResult.Failure)
        assertEquals("CLEAR_TEXT_HTTP_FORBIDDEN", res.error.code)

        val resLocal = EndpointPolicy.validate("http://localhost:3000", BuildMode.RELEASE)
        assertTrue(resLocal is ApiResult.Failure)
        assertEquals("CLEAR_TEXT_HTTP_FORBIDDEN", resLocal.error.code)
    }

    @Test
    fun testDisallowedDebugHttpHostsRejected() {
        val res = EndpointPolicy.validate("http://example.com:3000", BuildMode.DEBUG)
        assertTrue(res is ApiResult.Failure)
        assertEquals("DEBUG_HTTP_HOST_DISALLOWED", res.error.code)

        val resWrongPort = EndpointPolicy.validate("http://localhost:8080", BuildMode.DEBUG)
        assertTrue(resWrongPort is ApiResult.Failure)
        assertEquals("DEBUG_HTTP_HOST_DISALLOWED", resWrongPort.error.code)
    }

    @Test
    fun testUserinfoSubpathsQueryFragmentRejected() {
        val userinfoRes = EndpointPolicy.validate("https://user:pass@hub.example.com", BuildMode.RELEASE)
        assertTrue(userinfoRes is ApiResult.Failure)
        assertEquals("USERINFO_FORBIDDEN", userinfoRes.error.code)

        val subpathRes = EndpointPolicy.validate("https://hub.example.com/api/v1", BuildMode.RELEASE)
        assertTrue(subpathRes is ApiResult.Failure)
        assertEquals("PATH_FORBIDDEN", subpathRes.error.code)

        val queryRes = EndpointPolicy.validate("https://hub.example.com?foo=bar", BuildMode.RELEASE)
        assertTrue(queryRes is ApiResult.Failure)
        assertEquals("QUERY_FRAGMENT_FORBIDDEN", queryRes.error.code)

        val fragRes = EndpointPolicy.validate("https://hub.example.com#section", BuildMode.RELEASE)
        assertTrue(fragRes is ApiResult.Failure)
        assertEquals("QUERY_FRAGMENT_FORBIDDEN", fragRes.error.code)
    }

    @Test
    fun testDefaultHttpsPortNormalized() {
        val raw = "https://hub.example.com:443"
        val res = EndpointPolicy.validate(raw, BuildMode.RELEASE)
        assertTrue(res is ApiResult.Success)
        assertEquals("https://hub.example.com", res.value)
    }

    @Test
    fun testInvalidCharactersAndPortBounds() {
        val withSpace = EndpointPolicy.validate("https://hub .com", BuildMode.RELEASE)
        assertTrue(withSpace is ApiResult.Failure)
        assertEquals("INVALID_ENDPOINT_CHARACTERS", withSpace.error.code)

        val withBackslash = EndpointPolicy.validate("""https://hub.com\path""", BuildMode.RELEASE)
        assertTrue(withBackslash is ApiResult.Failure)
        assertEquals("INVALID_ENDPOINT_CHARACTERS", withBackslash.error.code)

        val invalidPort = EndpointPolicy.validate("https://hub.example.com:99999", BuildMode.RELEASE)
        assertTrue(invalidPort is ApiResult.Failure)
        assertEquals("INVALID_PORT", invalidPort.error.code)

        val nonNumericPort = EndpointPolicy.validate("https://hub.example.com:abc", BuildMode.RELEASE)
        assertTrue(nonNumericPort is ApiResult.Failure)
        assertEquals("INVALID_PORT", nonNumericPort.error.code)
    }
}
