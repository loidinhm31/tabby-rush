package dev.doggo.desktop

import java.io.File

/**
 * Loads environment variables from .env files with fallbacks across candidate directories.
 */
object DotEnvLoader {

    /**
     * Finds and loads the first existing .env file from candidate locations:
     * 1. Explicit file parameter
     * 2. System property `doggo.env.file` or env var `DOGGO_ENV_FILE`
     * 3. Working directory `./.env`
     * 4. Parent directories (`../.env`, `../../.env`)
     * 5. Optional baseDir
     */
    fun load(customFile: File? = null, baseDir: File? = null): Map<String, String> {
        val candidates = mutableListOf<File>()
        if (customFile != null) {
            candidates.add(customFile)
        }
        val explicitPath = System.getProperty("doggo.env.file")?.takeIf { it.isNotBlank() }
            ?: System.getenv("DOGGO_ENV_FILE")?.takeIf { it.isNotBlank() }
        if (explicitPath != null) {
            candidates.add(File(explicitPath))
        }
        candidates.add(File(".env"))
        candidates.add(File("../.env"))
        candidates.add(File("../../.env"))
        if (baseDir != null) {
            candidates.add(File(baseDir, ".env"))
        }

        val target = candidates.firstOrNull { it.isFile && it.exists() } ?: return emptyMap()
        return parse(target)
    }

    /**
     * Parses key-value pairs from a .env file format:
     * - Skips empty lines and lines starting with '#'
     * - Strips leading/trailing whitespace
     * - Splits on first '=' to allow '=' in values
     * - Unquotes single and double quoted values
     */
    fun parse(file: File): Map<String, String> {
        if (!file.exists() || !file.isFile) return emptyMap()
        val result = mutableMapOf<String, String>()
        try {
            file.forEachLine(Charsets.UTF_8) { rawLine ->
                val line = rawLine.trim()
                if (line.isNotEmpty() && !line.startsWith("#")) {
                    val idx = line.indexOf('=')
                    if (idx > 0) {
                        val key = line.substring(0, idx).trim()
                        var value = line.substring(idx + 1).trim()
                        if (value.length >= 2 && (
                            (value.startsWith("\"") && value.endsWith("\"")) ||
                            (value.startsWith("'") && value.endsWith("'"))
                        )) {
                            value = value.substring(1, value.length - 1)
                        }
                        result[key] = value
                    }
                }
            }
        } catch (_: Exception) {}
        return result
    }
}
