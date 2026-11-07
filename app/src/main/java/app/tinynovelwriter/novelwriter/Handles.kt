package app.tinynovelwriter.novelwriter

/**
 * A novelWriter item handle: exactly 13 lower-case hex characters.
 * Source: novelWriter 26.3b1, `novelwriter/common.py` `isHandle`.
 */
fun isHandle(value: String?): Boolean =
    value != null && value.length == 13 && value.all { it in '0'..'9' || it in 'a'..'f' }
