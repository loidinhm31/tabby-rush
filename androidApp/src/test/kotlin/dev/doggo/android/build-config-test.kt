package dev.doggo.android

import kotlin.test.Test
import kotlin.test.assertEquals

class BuildConfigTest {

    @Test
    fun testBuildConfigValuesLoadedFromDotEnv() {
        assertEquals("https://api-glean-hub.qm-hub-v001.cloud", BuildConfig.HUB_ORIGIN_DEBUG)
        assertEquals("https://api-glean-hub.qm-hub-v001.cloud", BuildConfig.HUB_ORIGIN_RELEASE)
        assertEquals("XoaJSoximq8EhK06IjXAQCOqKqRtM6qy", BuildConfig.HUB_API_KEY)
    }
}
