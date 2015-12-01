package main

val log2 = Math.log(2.0)

/**
 * Converts a value from normal tile format to its encoded representation (e.g. 2 -> 1 or 4 -> 2)
 * @return the encoded value
 */
fun encodeValue(value: Int): Int {
    return (Math.log(value.toDouble()) / log2 + 0.5).toInt()
}

/**
 * Converts a value from its encoded representation to the normal tile format to (e.g. 1 -> 2 or 2 -> 4)
 * @return the decoded value
 */
fun decodeValue(value: Int): Int {
    return when (value) {
        0 -> 0
        else -> 1 shl value.toInt()
    }
}