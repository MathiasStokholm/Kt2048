package main

// Heuristic scoring settings
val SCORE_LOST_PENALTY = 200000.0;
val SCORE_MONOTONICITY_POWER = 4.0;
val SCORE_MONOTONICITY_WEIGHT = 47.0;
val SCORE_SUM_POWER = 3.5;
val SCORE_SUM_WEIGHT = 11.0;
val SCORE_MERGES_WEIGHT = 700.0;
val SCORE_EMPTY_WEIGHT = 270.0;

// Number of possible rows
val ROW_COMBINATIONS = 32 * 32 * 32 * 32

data class Row(val data: Int = 0) {
    constructor(longData: Long): this(longData.toInt())

    fun set(index: Int, value: Int): Row {
        val bitPosition = index * 5
        val clearedPart = data and ((0x1F shl bitPosition)).inv();
        return copy(clearedPart or (value shl bitPosition))
    }

    fun clear(index: Int): Row {
        return set(index, 0)
    }

    operator fun get(index: Int): Int {
        val bitPosition = index * 5
        return (data and (0x1F shl bitPosition)) shr bitPosition
    }

    fun reversed(): Row {
        return copy((get(3) shl 0) or
                (get(2) shl 5) or
                (get(1) shl 10) or
                (get(0) shl 15))
    }

    fun move(): Pair<Row, Boolean> {
        var dataCopy = this

        //println("Moving part: $dataCopy")
        var changed = false
        for (index in (0..(GRID_SIZE - 1)).reversed()) {
            for (moves in 0 .. (GRID_SIZE - 1 - index)) {
                val current = dataCopy[index - 1 + moves]
                if (current == 0)
                    break

                val next = dataCopy[index + moves]
                if (next == 0) {
                    changed = true
                    dataCopy = dataCopy.set(index + moves, current + next)
                    dataCopy = dataCopy.clear(index - 1 + moves)
                } else if (current == next) {
                    changed = true
                    dataCopy = dataCopy.set(index + moves, current + 1)
                    dataCopy = dataCopy.clear(index - 1 + moves)
                    break
                }
            }
        }

        return Pair(dataCopy, changed)
    }

    override fun toString(): String {
        val builder = StringBuilder()
        (0..3).map { builder.append("${get(it)} ") }
        return builder.toString()
    }
}

// Construct maps of all possible moves (of a single row from left to right)
val moveMap = (0..ROW_COMBINATIONS).map { Row(it).move() }.toTypedArray()

// Construct maps of all possible moves (of a single row from right to left)
val moveMapReversed = (0..ROW_COMBINATIONS).map { Row(it) }.map {
    val (result, success) = it.reversed().move()
    Pair(result.reversed(), success)
}.toTypedArray()

// Construct maps of scores for all possible rows
val monotocityMap = (0..ROW_COMBINATIONS).map { Row(it) }.map {
    var sum = 0;
    var empty = 0;
    var merges = 0;

    var prev = 0;
    var counter = 0;
    for (i in 0..3) {
        val rank = it[i];
        sum += Math.pow(rank.toDouble(), SCORE_SUM_POWER).toInt();
        if (rank == 0) {
            empty++;
        } else {
            if (prev == rank) {
                counter++;
            } else if (counter > 0) {
                merges += 1 + counter;
                counter = 0;
            }
            prev = rank;
        }
    }
    if (counter > 0) {
        merges += 1 + counter;
    }

    var monotonicity_left = 0;
    var monotonicity_right = 0;
    for (i in 1 .. 3) {
        if (it[i-1] > it[i]) {
            monotonicity_left += (Math.pow(it[i-1].toDouble(), SCORE_MONOTONICITY_POWER) - Math.pow(it[i].toDouble(), SCORE_MONOTONICITY_POWER)).toInt();
        } else {
            monotonicity_right += (Math.pow(it[i].toDouble(), SCORE_MONOTONICITY_POWER) - Math.pow(it[i-1].toDouble(), SCORE_MONOTONICITY_POWER)).toInt();
        }
    }
    val monotonicity = Math.min(monotonicity_left, monotonicity_right)

    (SCORE_LOST_PENALTY  +
            SCORE_EMPTY_WEIGHT * empty +
            SCORE_MERGES_WEIGHT * merges -
            SCORE_MONOTONICITY_WEIGHT * monotonicity -
            SCORE_SUM_WEIGHT * sum).toInt()
}.toTypedArray()