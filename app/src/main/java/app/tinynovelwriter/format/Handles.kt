package app.tinynovelwriter.format

/** A novelWriter item handle: exactly 13 lower-case hex characters. */
fun isHandle(value: String?): Boolean =
    value != null && value.length == 13 && value.all { it in '0'..'9' || it in 'a'..'f' }
