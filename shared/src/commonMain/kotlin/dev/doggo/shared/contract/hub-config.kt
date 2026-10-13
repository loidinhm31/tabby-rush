package dev.doggo.shared.contract

enum class BuildMode {
    DEBUG,
    RELEASE
}

data class HubConfig(
    val canonicalOrigin: String,
    val appId: String = "doggo",
    val appApiKey: String,
    val buildMode: BuildMode
) {
    override fun toString(): String {
        return "HubConfig(canonicalOrigin='$canonicalOrigin', appId='$appId', appApiKey=[REDACTED], buildMode=$buildMode)"
    }
}
