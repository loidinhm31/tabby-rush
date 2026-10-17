package dev.tabbyrush.shared.contract

object StrictSyncV2Digest {
    fun buildPreimage(
        domain: String = StrictSyncV2Contract.PROTOCOL_VERSION,
        appId: String,
        userId: String,
        epoch: String,
        tableName: String,
        rowId: String,
        opId: String,
        kind: StrictOperationKind,
        expectedRevision: Long,
        data: StrictV2TodoData?
    ): ByteArray {
        require(expectedRevision >= 0) { "expectedRevision must be non-negative" }
        if (kind == StrictOperationKind.DELETE) {
            require(data == null) { "DELETE operation must not have data payload" }
        } else {
            require(data != null) { "$kind operation must have non-null data payload" }
            data.validate()
        }

        val domainB = domain.encodeToByteArray()
        val appIdB = appId.encodeToByteArray()
        val userB = userId.encodeToByteArray()
        val epochB = epoch.encodeToByteArray()
        val tableB = tableName.encodeToByteArray()
        val rowB = rowId.encodeToByteArray()
        val opB = opId.encodeToByteArray()
        val kindB = kind.wireName.encodeToByteArray()

        val dataB: ByteArray = if (kind != StrictOperationKind.DELETE && data != null) {
            encodeV2Data(data)
        } else ByteArray(0)

        val totalLen = 4 + domainB.size + 4 + appIdB.size + 4 + userB.size +
                4 + epochB.size + 4 + tableB.size + 4 + rowB.size +
                4 + opB.size + 4 + kindB.size + 8 + dataB.size
        val buffer = ByteArray(totalLen)
        var offset = 0
        fun writeStr(b: ByteArray) {
            val len = b.size
            buffer[offset++] = (len ushr 24).toByte(); buffer[offset++] = (len ushr 16).toByte()
            buffer[offset++] = (len ushr 8).toByte(); buffer[offset++] = len.toByte()
            b.copyInto(buffer, offset); offset += len
        }
        listOf(domainB, appIdB, userB, epochB, tableB, rowB, opB, kindB).forEach(::writeStr)
        for (i in 7 downTo 0) buffer[offset++] = ((expectedRevision ushr (i * 8)) and 0xFFL).toByte()
        if (dataB.isNotEmpty()) dataB.copyInto(buffer, offset)
        return buffer
    }

    fun computeDigest(
        domain: String = StrictSyncV2Contract.PROTOCOL_VERSION,
        appId: String,
        userId: String,
        epoch: String,
        tableName: String,
        rowId: String,
        opId: String,
        kind: StrictOperationKind,
        expectedRevision: Long,
        data: StrictV2TodoData?
    ): String {
        val preimage = buildPreimage(
            domain, appId, userId, epoch, tableName, rowId, opId, kind, expectedRevision, data
        )
        return StrictSyncDigest.sha256Hex(preimage)
    }

    private fun encodeV2Data(data: StrictV2TodoData): ByteArray {
        val parts = mutableListOf<ByteArray>()
        fun putStr(s: String) {
            val b = s.encodeToByteArray()
            val prefix = byteArrayOf((b.size ushr 24).toByte(), (b.size ushr 16).toByte(), (b.size ushr 8).toByte(), b.size.toByte())
            parts.add(prefix); parts.add(b)
        }
        fun putNullableStr(s: String?) {
            if (s == null) {
                parts.add(byteArrayOf(0x00))
            } else {
                parts.add(byteArrayOf(0x01))
                putStr(s)
            }
        }
        fun putBool(b: Boolean) {
            parts.add(byteArrayOf(if (b) 0x01 else 0x00))
        }
        fun putI64(v: Long) {
            val b = ByteArray(8)
            for (i in 7 downTo 0) b[7 - i] = ((v ushr (i * 8)) and 0xFFL).toByte()
            parts.add(b)
        }
        fun putNullableI64(v: Long?) {
            if (v == null) {
                parts.add(byteArrayOf(0x00))
            } else {
                parts.add(byteArrayOf(0x01))
                putI64(v)
            }
        }

        putStr(data.id)
        putStr(data.title)
        putBool(data.completed)
        putI64(data.createdAt)
        putNullableStr(data.deadlineDate)
        putNullableStr(data.deadlineTime)
        putNullableStr(data.deadlineZone)
        putNullableI64(data.deadlineOffsetSeconds)
        putNullableStr(data.reminderAnchorTime)
        putNullableI64(data.deadlineAnchorEpochMs)
        putNullableI64(data.deadlineOverdueEpochMs)
        putBool(data.reminderEnabled)
        putI64(data.reminderLeadMinutes)
        putNullableI64(data.reminderIntervalMinutes)
        putI64(data.reminderCount)
        putNullableStr(data.scheduleToken)

        val total = parts.sumOf { it.size }
        val out = ByteArray(total)
        var pos = 0
        for (p in parts) {
            p.copyInto(out, pos)
            pos += p.size
        }
        return out
    }
}
