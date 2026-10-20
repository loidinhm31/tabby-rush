package dev.tabbyrush.android

import dev.tabbyrush.shared.contract.ApiResult
import dev.tabbyrush.shared.contract.BuildMode
import dev.tabbyrush.shared.contract.EndpointPolicy
import dev.tabbyrush.shared.contract.HubConfig
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BuildConfigTest {
    @Test fun debugTargetObeysEndpointPolicyAndConfigurationNeverPrintsKey() {
        assertIs<ApiResult.Success<String>>(EndpointPolicy.validate(BuildConfig.HUB_ORIGIN_DEBUG, BuildMode.DEBUG))
        val config = HubConfig(BuildConfig.HUB_ORIGIN_DEBUG, "tabby-rush", "disposable-test-secret", BuildMode.DEBUG)
        assertFalse(config.toString().contains("disposable-test-secret"))
        assertTrue(BuildConfig.APPLICATION_ID == "dev.tabbyrush.android" || BuildConfig.APPLICATION_ID == "dev.tabbyrush.android.standardsmoke")
    }
}
