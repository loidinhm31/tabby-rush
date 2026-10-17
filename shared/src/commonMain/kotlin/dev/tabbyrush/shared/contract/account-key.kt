package dev.tabbyrush.shared.contract

data class AccountKey(val value: String) {
    init {
        require(value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' }) {
            "AccountKey must be exactly 64 lowercase hexadecimal characters"
        }
    }

    override fun toString(): String = "AccountKey($value)"

    companion object {
        fun create(canonicalOrigin: String, userId: String): AccountKey {
            val originBytes = canonicalOrigin.encodeToByteArray()
            val userBytes = userId.encodeToByteArray()

            val totalLen = 4 + originBytes.size + 4 + userBytes.size
            val buffer = ByteArray(totalLen)
            var offset = 0

            fun writeU32Be(v: Int) {
                buffer[offset++] = (v ushr 24).toByte()
                buffer[offset++] = (v ushr 16).toByte()
                buffer[offset++] = (v ushr 8).toByte()
                buffer[offset++] = v.toByte()
            }

            writeU32Be(originBytes.size)
            originBytes.copyInto(buffer, offset)
            offset += originBytes.size

            writeU32Be(userBytes.size)
            userBytes.copyInto(buffer, offset)
            offset += userBytes.size

            val hashHex = StrictSyncDigest.sha256Hex(buffer)
            return AccountKey(hashHex)
        }
    }
}
