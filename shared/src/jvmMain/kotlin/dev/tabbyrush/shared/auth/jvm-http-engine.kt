package dev.tabbyrush.shared.auth

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.java.Java
import java.time.Duration

actual fun createPlatformHttpEngine(): HttpClientEngine {
    return Java.create {
        config {
            connectTimeout(Duration.ofSeconds(10))
        }
    }
}
