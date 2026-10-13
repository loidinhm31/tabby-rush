package dev.doggo.shared.contract

object TitlePolicy {
    private val WHITE_SPACE_CODE_POINTS = setOf(
        0x0009, 0x000A, 0x000B, 0x000C, 0x000D,
        0x0020, 0x0085, 0x00A0, 0x1680,
        0x2000, 0x2001, 0x2002, 0x2003, 0x2004, 0x2005, 0x2006, 0x2007, 0x2008, 0x2009, 0x200A,
        0x2028, 0x2029, 0x202F, 0x205F, 0x3000
    )

    private fun isWhiteSpace(cp: Int): Boolean = cp in WHITE_SPACE_CODE_POINTS

    fun normalizeAndValidate(raw: String): ApiResult<String> {
        val codePoints = mutableListOf<Int>()
        var i = 0
        while (i < raw.length) {
            val c1 = raw[i]
            if (c1.isHighSurrogate()) {
                if (i + 1 < raw.length && raw[i + 1].isLowSurrogate()) {
                    val c2 = raw[i + 1]
                    val cp = ((c1.code - 0xD800) shl 10) + (c2.code - 0xDC00) + 0x10000
                    codePoints.add(cp)
                    i += 2
                } else {
                    return ApiResult.Failure(
                        SafeError.Validation(
                            code = "INVALID_UNICODE",
                            message = "Title contains unpaired surrogate",
                            field = "title"
                        )
                    )
                }
            } else if (c1.isLowSurrogate()) {
                return ApiResult.Failure(
                    SafeError.Validation(
                        code = "INVALID_UNICODE",
                        message = "Title contains unpaired surrogate",
                        field = "title"
                    )
                )
            } else {
                codePoints.add(c1.code)
                i += 1
            }
        }

        var start = 0
        while (start < codePoints.size && isWhiteSpace(codePoints[start])) {
            start++
        }

        var end = codePoints.size
        while (end > start && isWhiteSpace(codePoints[end - 1])) {
            end--
        }

        val trimmed = codePoints.subList(start, end)

        for (cp in trimmed) {
            if (cp in 0x0000..0x001F || cp == 0x007F) {
                return ApiResult.Failure(
                    SafeError.Validation(
                        code = "INVALID_CONTROL_CHARACTER",
                        message = "Title contains invalid internal control characters",
                        field = "title"
                    )
                )
            }
        }

        val scalarCount = trimmed.size
        if (scalarCount == 0) {
            return ApiResult.Failure(
                SafeError.Validation(
                    code = "TITLE_EMPTY",
                    message = "Title must contain 1–200 Unicode scalar values",
                    field = "title"
                )
            )
        }

        if (scalarCount > 200) {
            return ApiResult.Failure(
                SafeError.Validation(
                    code = "TITLE_TOO_LONG",
                    message = "Title must contain 1–200 Unicode scalar values (found $scalarCount)",
                    field = "title"
                )
            )
        }

        val sb = StringBuilder()
        for (cp in trimmed) {
            if (cp <= 0xFFFF) {
                sb.append(cp.toChar())
            } else {
                val u = cp - 0x10000
                sb.append((0xD800 + (u shr 10)).toChar())
                sb.append((0xDC00 + (u and 0x3FF)).toChar())
            }
        }

        return ApiResult.Success(sb.toString())
    }
}
