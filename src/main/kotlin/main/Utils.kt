package main

val log2 = Math.log(2.0)

fun encodeValue(value: Int): Int {
    return (Math.log(value.toDouble()) / log2 + 0.5).toInt()
}

fun decodeValue(value: Int): Int {
    return when (value) {
        0 -> 0
        else -> 1 shl value.toInt()
    }
}