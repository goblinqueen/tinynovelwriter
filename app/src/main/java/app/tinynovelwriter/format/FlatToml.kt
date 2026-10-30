package app.tinynovelwriter.format

/**
 * The flat subset of TOML used by novelWriter document headers: one `key = value` per
 * line, with basic or literal strings, integers or booleans. Anything else is rejected,
 * like Python's tomllib would reject invalid TOML.
 */
internal object FlatToml {

    private val BARE_KEY = Regex("[A-Za-z0-9_-]+")
    private val INTEGER = Regex("[+-]?(0|[1-9](_?[0-9])*)")

    /** @throws IllegalArgumentException on anything outside the supported subset. */
    fun parse(text: String): Map<String, Any> {
        val result = LinkedHashMap<String, Any>()
        for ((index, rawLine) in text.split('\n').withIndex()) {
            val line = rawLine.trim(' ', '\t')
            if (line.isEmpty() || line.startsWith("#")) continue

            val eq = line.indexOf('=')
            require(eq > 0) { "line ${index + 1}: expected key = value" }
            val key = line.substring(0, eq).trim(' ', '\t')
            require(BARE_KEY.matches(key)) { "line ${index + 1}: unsupported key '$key'" }
            require(key !in result) { "line ${index + 1}: duplicate key '$key'" }

            val (value, rest) = parseValue(line.substring(eq + 1).trimStart(' ', '\t'), index + 1)
            val tail = rest.trim(' ', '\t')
            require(tail.isEmpty() || tail.startsWith("#")) { "line ${index + 1}: unexpected text after value" }
            result[key] = value
        }
        return result
    }

    private fun parseValue(s: String, line: Int): Pair<Any, String> = when {
        s.startsWith("\"\"\"") || s.startsWith("'''") ->
            throw IllegalArgumentException("line $line: multi-line strings are not supported")
        s.startsWith("\"") -> parseBasicString(s, line)
        s.startsWith("'") -> {
            val end = s.indexOf('\'', 1)
            require(end > 0) { "line $line: unterminated string" }
            s.substring(1, end) to s.substring(end + 1)
        }
        s.startsWith("true") -> true to s.removePrefix("true")
        s.startsWith("false") -> false to s.removePrefix("false")
        else -> {
            val token = s.takeWhile { it != ' ' && it != '\t' && it != '#' }
            require(INTEGER.matches(token)) { "line $line: unsupported value '$token'" }
            token.replace("_", "").toLong() to s.substring(token.length)
        }
    }

    private fun parseBasicString(s: String, line: Int): Pair<String, String> {
        val sb = StringBuilder()
        var i = 1
        while (i < s.length) {
            val c = s[i]
            when {
                c == '"' -> return sb.toString() to s.substring(i + 1)
                c == '\\' -> {
                    require(i + 1 < s.length) { "line $line: unterminated string" }
                    when (val e = s[i + 1]) {
                        'b' -> sb.append('\b')
                        't' -> sb.append('\t')
                        'n' -> sb.append('\n')
                        'f' -> sb.append('\u000C')
                        'r' -> sb.append('\r')
                        'e' -> sb.append('\u001B')
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        'u', 'U' -> {
                            val len = if (e == 'u') 4 else 8
                            val hex = s.substring(i + 2, minOf(i + 2 + len, s.length))
                            require(hex.length == len && hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
                                "line $line: bad unicode escape"
                            }
                            val cp = hex.toInt(16)
                            require(Character.isValidCodePoint(cp) && cp !in 0xD800..0xDFFF) {
                                "line $line: bad unicode escape"
                            }
                            sb.appendCodePoint(cp)
                            i += len
                        }
                        else -> throw IllegalArgumentException("line $line: bad escape '\\$e'")
                    }
                    i += 2
                    continue
                }
                c.code < 0x20 && c != '\t' || c.code == 0x7F ->
                    throw IllegalArgumentException("line $line: control character in string")
                else -> sb.append(c)
            }
            i++
        }
        throw IllegalArgumentException("line $line: unterminated string")
    }
}
