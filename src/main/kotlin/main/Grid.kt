package main

/**
 * Class used to represent a board as a 4x4 Grid.
 * The entire board state is encoded as 2 64-bit integers, with each tile taking up 5 bits.
 */
data class Grid(val data1: Long, val data2: Long) {

    /**
     * Conducts a move in the provided direction
     * @param direction the Direction to move
     * @return a new Grid resulting from moving in the provided direction
     */
    fun move(direction: Direction): Grid {
        // Check if we need to transpose the grid before moving rows
        val (component1, component2) = if (!direction.moveAlongColumn) Pair(data1, data2) else {
            val transposedGrid = transpose()
            Pair(transposedGrid.data1, transposedGrid.data2)
        }

        // Extract data into Rows
        val partialData = listOf(Row(component1 and 0xFFFFF), Row((component1 shr 20) and 0xFFFFF), Row(component2 and 0xFFFFF), Row((component2 shr 20) and 0xFFFFF))

        // Lookup moves or reversed moves based on direction
        val finalRows = partialData.map { if (!direction.reverseNeeded) moveMap[it.data] else moveMapReversed[it.data] }

        // Check whether move is valid
        val validMove = finalRows[0].second or finalRows[1].second or finalRows[2].second or finalRows[3].second
        return if (validMove) {
            // Create new Grid from Rows after move
            val newGrid = Grid(finalRows[0].first.data.toLong() or (finalRows[1].first.data.toLong() shl 20),
                    finalRows[2].first.data.toLong() or (finalRows[3].first.data.toLong() shl 20))

            // Check whether we need to transpose results to move back to original coordinate system
            if (!direction.moveAlongColumn) {
                newGrid
            } else {
                newGrid.transpose()
            }
        } else {
            // An invalid move will result in an empty grid with score 0
            Grid(0, 0)
        }
    }

    /**
     * Creates a copy of this Grid with a new tile added
     * @param row the row at which the new tile should be placed
     * @param column the column at which the new tile should be placed
     * @param value the value to assign to the new tile
     * @return a copy of this Grid with the a new tile added
     */
    fun copyAndSet(row: Int, column: Int, value: Int): Grid {
        val encodedValue = encodeValue(value).toLong()
        if (row * 4 + column < 8) {
            val bitPosition = (row * 4 + column) * 5
            val clearedPart = data1 and ((0x1FL shl bitPosition)).inv();
            return copy(clearedPart or (encodedValue shl bitPosition), data2)
        } else {
            val bitPosition = (row * 4 + column - 8) * 5
            val clearedPart = data2 and ((0x1FL shl bitPosition).toLong()).inv();
            return copy(data1, clearedPart or (encodedValue shl bitPosition))
        }
    }

    /**
     * Looks up the value of the tile located at the specified position
     * @param row the row of the tile
     * @param column the column of the tile
     * @return the value of the tile located at the specified position
     */
    fun getTile(row: Int, column: Int): Int {
        val part = when (row * 4 + column) {
            in 0..7 -> data1
            else -> data2
        }

        val correction = if (row * 4 + column < 8) 0 else 8
        val bitPosition = row * 4 + column - correction
        return getTile(part, bitPosition).toInt()
    }

    /**
     * @return a transposed copy of this Grid
     */
    private fun transpose(): Grid {
        val newData1 =  getTile(data1, 0) + (getTile(data1, 4) shl 5) + (getTile(data2, 0) shl 10) + (getTile(data2, 4) shl 15) +
                (getTile(data1, 1) shl 20) + (getTile(data1, 5) shl 25) + (getTile(data2, 1) shl 30) + (getTile(data2, 5) shl 35)
        val newData2 =  getTile(data1, 2) + (getTile(data1, 6) shl 5) + (getTile(data2, 2) shl 10) + (getTile(data2, 6) shl 15) +
                (getTile(data1, 3) shl 20) + (getTile(data1, 7) shl 25) + (getTile(data2, 3) shl 30) + (getTile(data2, 7) shl 35)
        return Grid(newData1, newData2)
    }

    /**
     * @return the score of this Grid
     */
    fun score(): Int {
        // If Grid is empty, simply return 0 right away
        if ((data1 or data2) == 0L) {
            return 0
        }

        // Score grid by looking up the score of each row of this Grid and a transposed copy of this Grid
        val transposedGrid = transpose()
        return scoreMap[(data1 and 0xFFFFF).toInt()] + scoreMap[((data1 shr 20) and 0xFFFFF).toInt()] +
                scoreMap[(data2 and 0xFFFFF).toInt()] + scoreMap[((data2 shr 20) and 0xFFFFF).toInt()] +
                scoreMap[(transposedGrid.data1 and 0xFFFFF).toInt()] + scoreMap[((transposedGrid.data1 shr 20) and 0xFFFFF).toInt()] +
                scoreMap[(transposedGrid.data2 and 0xFFFFF).toInt()] + scoreMap[((transposedGrid.data2 shr 20) and 0xFFFFF).toInt()]
    }

    /**
     * Prints the layout of this Grid in a human-readable fashion
     * @return a reference to this Grid to allow chaining of calls
     */
    fun print(): Grid {
        print(toString())
        return this
    }

    /**
     * Creates a String representing this Grid by printing out the items of each row separated by spaces, and each row
     * separated by a new line
     * @return a String representing this Grid
     */
    override fun toString(): String {
        val builder = StringBuilder()
        for (i in 0..(GRID_SIZE - 1)) {
            for (j in 0..(GRID_SIZE - 1)) {
                builder.append(when (i) {
                    in 0..1 -> "${decodeValue(getTile(data1, i*4+j).toInt())} "
                    else -> "${decodeValue(getTile(data2, i*4+j-8).toInt())} "
                })
            }
            builder.append("\n")
        }
        builder.append("\n")
        return builder.toString()
    }
}

/**
 * Looks up the value of the tile located at the specified position in the provided integer
 * @param fromPart an integer representing half of a Grid
 * @param index the index at which the tile value should be looked up
 * @return the value of the tile located at the specified index
 */
fun getTile(fromPart: Long, index: Int): Long {
    val bitPosition = index * 5
    return ((fromPart and (0x1FL shl bitPosition)) shr bitPosition)
}

/**
 * Utility method to create a Grid from a list of Tiles
 * @param tileList a list of Tiles, can be empty
 * @return a Grid initialized with the provided tiles
 */
fun newInstance(tileList : List<Tile> = emptyList()): Grid {
    val data1 = tileList.filter { it.row * 4 + it.column < 8 }.map {
        val valueToStore = encodeValue(it.value).toLong()
        val bitPosition = (it.row * 4 + it.column) * 5
        valueToStore shl bitPosition
    }.sum()

    val data2 = tileList.filter { it.row * 4 + it.column >= 8 }.map {
        val valueToStore = encodeValue(it.value).toLong()
        val bitPosition = (it.row * 4 + it.column - 8) * 5
        valueToStore shl bitPosition
    }.sum()

    return Grid(data1, data2)
}