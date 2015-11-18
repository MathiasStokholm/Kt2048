package main

import org.openqa.selenium.*
import org.openqa.selenium.chrome.ChromeDriver
import rx.Observable
import rx.schedulers.Schedulers

// Top level constants
val RUNS = 10
val log2 = Math.log(2.0)

data class Tile(val column: Int, val row: Int, val value: Int, val merged: Boolean = false) {
    operator fun plus(other: Tile): Tile {
        if (other.value == 0) {
            return other.copy(value = value + other.value, merged = merged)
        } else {
            return other.copy(value = value + other.value, merged = true)
        }
    }

    fun reset(): Tile {
        return Tile(column, row, 0)
    }

    override fun toString(): String {
        return " $value "
    }
}

enum class Direction(useColumn: Boolean, reverse: Boolean, key: Keys) {
    LEFT(false, true, Keys.ARROW_LEFT), UP(true, true, Keys.UP),
    RIGHT(false, false, Keys.ARROW_RIGHT), DOWN(true, false, Keys.ARROW_DOWN);
    val moveAlongColumn = useColumn
    val reverseNeeded = reverse
    val key = key
}

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

    override fun toString(): String {
        val builder = StringBuilder()
        (0..3).map { builder.append("${get(it)} ") }
        return builder.toString()
    }
}

fun newInstance(tileList : List<Tile> = emptyList()): NewGrid {
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

    return NewGrid(data1, data2)
}

data class NewGrid(val data1: Long, val data2: Long) {
    val GRID_SIZE = 4;

    fun move(direction: Direction): NewGrid {
        val (component1, component2) = if (!direction.moveAlongColumn) Pair(data1, data2) else {
            val transposedGrid = transpose()
            Pair(transposedGrid.data1, transposedGrid.data2)
        }

        val partialData = listOf(Row(component1 and 0xFFFFF), Row(component1 shr 20), Row(component2 and 0xFFFFF), Row(component2 shr 20))

        val outputLists = partialData
            .map { if (direction.reverseNeeded) it.reversed() else it }
            .map { moveList(it) }

        val finalRows = if (!direction.reverseNeeded) outputLists.map { it.first } else {
            outputLists.map { it.first.reversed() }
        }

         // Check whether move is valid
        val validMove = outputLists[0].second or outputLists[1].second or outputLists[2].second or outputLists[3].second
        return if (validMove) {
            val newGrid = NewGrid(finalRows[0].data.toLong() or (finalRows[1].data.toLong() shl 20),
                    finalRows[2].data.toLong() or (finalRows[3].data.toLong() shl 20))
            if (!direction.moveAlongColumn) {
                newGrid
            } else {
                newGrid.transpose()
            }
        } else {
            NewGrid(0, 0)
        }
    }

    fun moveList(part: Row): Pair<Row, Boolean> {
        var dataCopy = part

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

        //println("Result: $dataCopy")
        return Pair(dataCopy, changed)
    }

    fun setTile(row: Int, column: Int, value: Int): NewGrid {
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

    fun getTile(row: Int, column: Int): Int {
        val part = when (row * 4 + column) {
            in 0..7 -> data1
            else -> data2
        }

        val correction = if (row * 4 + column < 8) 0 else 8
        val bitPosition = row * 4 + column - correction
        return getTile(part, bitPosition).toInt()
    }

    fun getTile(fromPart: Long, index: Int): Long {
        val bitPosition = index * 5
        return ((fromPart and (0x1FL shl bitPosition)) shr bitPosition)
    }

    fun transpose(): NewGrid {
        val newData1 =  getTile(data1, 0) + (getTile(data1, 4) shl 5) + (getTile(data2, 0) shl 10) + (getTile(data2, 4) shl 15) +
                (getTile(data1, 1) shl 20) + (getTile(data1, 5) shl 25) + (getTile(data2, 1) shl 30) + (getTile(data2, 5) shl 35)
        val newData2 =  getTile(data1, 2) + (getTile(data1, 6) shl 5) + (getTile(data2, 2) shl 10) + (getTile(data2, 6) shl 15) +
                (getTile(data1, 3) shl 20) + (getTile(data1, 7) shl 25) + (getTile(data2, 3) shl 30) + (getTile(data2, 7) shl 35)
        return NewGrid(newData1, newData2)
    }

    fun score(): Int {
        var score = 0
        for (i in 0..7) {
            val tile = decodeValue(getTile(data1, i).toInt())
            score += tile * tile
        }
        for (i in 0..7) {
            val tile = decodeValue(getTile(data2, i).toInt())
            score += tile * tile
        }

        return score
    }

    fun print(): NewGrid {
        print(toString())
        return this
    }

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

fun encodeValue(value: Int): Int {
    return (Math.log(value.toDouble()) / log2 + 0.5).toInt()
}

fun decodeValue(value: Int): Int {
    return when (value) {
        0 -> 0
        else -> 1 shl value.toInt()
    }
}

class Grid(tileList : List<Tile> = emptyList()) {
    val GRID_SIZE = 4;

    val tiles: Array<Tile> = {
        val tempArray = Array(GRID_SIZE * GRID_SIZE, { i -> Tile(i / GRID_SIZE, i % GRID_SIZE, 0) })
        tileList.forEach { tempArray.set(it.column * GRID_SIZE + it.row, it.copy(merged = false)) }
        tempArray
    }.invoke()

    fun move(direction: Direction): Grid {
        val outputLists = tiles.groupBy { if (direction.moveAlongColumn) it.column else it.row }
                .map { it.value }
                .map { if (direction.reverseNeeded) it.reversed() else it }
                .map { moveList(it.toTypedArray())}
                .map {
                    if (direction.reverseNeeded) Pair(it.first.reversed().toTypedArray(), it.second)
                    else it
                }

        // Check whether move is valid
        val validMove = outputLists.map { it.second }.reduce { a, b -> a or b }
        return if (validMove) {
            Grid(outputLists.map { it.first }.flatMap { it.asList() })
        } else {
            Grid()
        }
    }

    fun moveList(part: Array<Tile>): Pair<Array<Tile>, Boolean> {
        //println("Moving part: ${part.map { it.toString() }}")
        var changed = false
        for (index in part.indices.drop(1).reversed()) {
            for (moves in 0 .. (part.size - 1 - index)) {
                val current = part[index - 1 + moves]
                if (current.value == 0)
                    break

                val next = part[index + moves]
                if (next.value == 0) {
                    changed = true
                    part.set(index + moves, current + next)
                    part.set(index - 1 + moves, current.reset())
                } else if (!current.merged && !next.merged && current.value == next.value) {
                    changed = true
                    part.set(index + moves, current + next)
                    part.set(index - 1 + moves, current.reset())
                    break
                }
            }
        }
        //println("Result: ${part.map { it.toString() }}")
        return Pair(part, changed)
    }

    fun score(): Int {
        val baseScore = tiles.map { it.value * it.value }.sum()
        //val maxTileDistance = tiles.sortedBy { it.value }.map { tile ->
        //    (((tile.column shl 1) + (tile.row shl 1))) * tile.value
        //}.sum()
        //val tile = tiles.sortedBy { it.value }.last()
        //val bonus = ((tile.column shl 1) + (tile.row shl 1)) * tile.value
        return baseScore// + bonus// + (maxTileDistance * baseScore * 0.5).toInt()
    }

    override fun toString(): String {
        val builder = StringBuilder()
        tiles.sortedBy { it.row }.forEachIndexed { i, tile ->
            builder.append(tile)
            if ((i + 1) % GRID_SIZE == 0 && i != tiles.size - 1)
                builder.appendln()
        }
        return builder.toString()
    }

    fun containedIn(other: Grid): Boolean {
        return tiles.filter { it.value != 0 }
                .filter { it !in other.tiles }
                .count() == 0
    }

    fun copy(tileList: List<Tile>) = Grid(tileList)
}

fun main(args: Array<String>) {
    System.setProperty("webdriver.chrome.driver","C:\\Users\\gedemis\\IdeaProjects\\Kot2048\\chromedriver.exe");

    val webDriver = ChromeDriver()
    webDriver.manage().window().size = Dimension(400, 700)
    webDriver.get("https://gabrielecirulli.github.io/2048/")
    webDriver.setup()

    val scores = (0 .. RUNS).map {
        var move : Direction? = null;
        val startTime = System.currentTimeMillis()
        var moves = 0;
        var gameContinued = false

        while (true) {
            val queryResult = webDriver.getTilesOptimized(move)
            if (queryResult.won && !gameContinued) {
                webDriver.continueGame()
                gameContinued = true
            }

            val currentGrid = newInstance(queryResult.tiles)
            val compStartTime = System.currentTimeMillis()

            val bestGuess = getBestMove2(currentGrid, 3)
            if (bestGuess.second == 0) {
                println("No directions to move! Ending game, computation took: ${System.currentTimeMillis() - compStartTime}")
                break
            }

            println("Moving in direction: ${bestGuess.first.name} with score: ${bestGuess.second}, computation took: ${System.currentTimeMillis() - compStartTime}")
            move = bestGuess.first

            moves++
        }

        val score = webDriver.findElementByClassName("score-container").text
        val time = (System.currentTimeMillis() - startTime)
        println("Game over! Score: " + score + ", moves: $moves, m/s: ${moves.toFloat()*1000 / time}")
        Thread.sleep(500)

        // Start new game
        webDriver.findElement(By.className("restart-button")).click()
        Thread.sleep(500)

        Triple(score, moves, time)
    }

    webDriver.quit()
}

fun ChromeDriver.setup() {
    val script = """
                    _func_tmp = GameManager.prototype.isGameTerminated;
                    GameManager.prototype.isGameTerminated = function() {
                        GameManager._instance = this;
                        return true;
                    };
                """

    if (this is JavascriptExecutor) {
        executeScript(script)

        keyboard.pressKey(Keys.ARROW_UP)
        Thread.sleep(100)
        keyboard.releaseKey(Keys.ARROW_UP)

        executeScript("GameManager.prototype.isGameTerminated = _func_tmp;")
    }
}

/**
 * Continue the game. Only works if the game is in the 'won' state
 */
fun WebDriver.continueGame() {
    // Wait for "continue" button to appear, then click
    Thread.sleep(750)
    findElement(By.className("keep-playing-button")).click()
}

data class TileQuery(val tiles: List<Tile>, val won: Boolean)
fun JavascriptExecutor.getTilesOptimized(direction: Direction? = null): TileQuery {
    val moveAction = when (direction) {
        Direction.UP -> "GameManager._instance.move(0)"
        Direction.RIGHT -> "GameManager._instance.move(1)"
        Direction.DOWN  -> "GameManager._instance.move(2)"
        Direction.LEFT  -> "GameManager._instance.move(3)"
        null -> ""
    }

    val script = """
                    $moveAction
                    return [GameManager._instance.won].concat(
                    GameManager._instance.grid.cells.reduce(function(a,b) {
                        return a.concat(b)
                    }).filter(function(tile) {
                        return tile != null
                    }).map(function(tile) {
                        return "" + tile.x + "," + tile.y + "," + tile.value }))
                 """

    val returnValue = executeScript(script)
    if (returnValue is List<*>) {
        return TileQuery(returnValue.filterIsInstance<String>().map {
            val parts = it.split(",")
            Tile(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
        }, returnValue[0] as Boolean)
    }

    return TileQuery(emptyList(), false)
}

//fun WebDriver.getTiles(): List<Tile> {
//    val script = """
//                    var tiles = document.getElementsByClassName('tile');
//                    var arr = [];
//                    for (var i = 0; i < tiles.length; i++) {
//                        arr.push(tiles[i].getAttribute('class'));
//                    }
//                    return arr
//                 """
//
//    // Execute lookup of grid elements in JS and return a list of class names that may then be parsed in Kotlin
//    if (this is JavascriptExecutor) {
//        val returnValue = executeScript(script)
//
//        // Above script will always return List<String>
//        if (returnValue is List<*>) {
//            return returnValue.filterIsInstance<String>().map {
//                val nameParts = it.split(" ")
//                val value = nameParts[1].split("-")[1].toInt()
//                val column = nameParts[2].split("-")[2].toInt() - 1
//                val row = nameParts[2].split("-")[3].toInt() - 1
//                Tile(column, row, value)
//            }.sortedByDescending{ it.value }.distinctBy { it.row + 10 * it.column }
//        }
//    }
//    return emptyList()
//}
//
//fun getBestMove(grid: Grid, depth: Int): Pair<Direction, Int> {
//    return Observable.just(null).flatMap {
//        Observable.just(Direction.LEFT, Direction.UP, Direction.RIGHT, Direction.DOWN)
//                .subscribeOn(Schedulers.computation())
//                .map {
//                    val movedGrid = grid.move(it)
//                    if (movedGrid.score() > 0) {
//                        val result = recursiveMove(movedGrid, depth - 1)
//                        Pair(it, result.mapIndexed { index, step -> step.second / (index + 1) }.sum() + movedGrid.score())
//                        //Pair(it, result.sumBy { it.second } + movedGrid.score())
//                        //Pair(it, movedGrid.score())
//                    } else {
//                        Pair(it, 0)
//                    }
//                }
//    }.toList().toBlocking().single()
//            .sortedBy { it.second }
//            .last()
//}
//
//fun recursiveMove(grid: Grid, depth: Int): List<Pair<Direction, Int>> {
//    if (depth == 0) {
//        return listOf(listOf(Direction.LEFT, Direction.UP, Direction.RIGHT, Direction.DOWN)
//                .map { Pair(it, grid.move(it).score()) }
//                .sortedBy { it.second }
//                .last())
//    } else {
//        val bestPath = listOf(Direction.LEFT, Direction.UP, Direction.RIGHT, Direction.DOWN)
//                .map { Pair(it, grid.move(it)) }
//                .filter { it.second.score() > 0 }
//                .map { Triple(it.first, it.second.score(), recursiveMove(it.second, depth - 1)) }
//                .sortedBy { it.third.map { it.second }.sum() }
//                .last()
//
//        return bestPath.third + Pair(bestPath.first, bestPath.second)
//    }
//}

data class State(val grid: NewGrid, val depth: Int, val maxNode: Boolean = false)

fun getBestMove2(grid: NewGrid, depth: Int): Pair<Direction, Int> {
    return Observable.just(null).flatMap {
        Observable.just(Direction.LEFT, Direction.UP, Direction.RIGHT, Direction.DOWN)
                .subscribeOn(Schedulers.computation())
                .map {
                    val movedGrid = grid.move(it)
                    if (movedGrid.score() <= 0) Pair(it, 0) else {
                        val bestScore = value(State(movedGrid, depth))
                        if (bestScore <= 0) Pair(it, movedGrid.score()) else Pair(it, bestScore)
                    }
                }
    }.toList().toBlocking().single()
            .sortedBy { it.second }
            .last()
}

fun value(state: State): Int {
    //println("Finding value with appro at depth ${state.depth}")
    if (state.depth == 0) {
        //println("Returning terminal value: ${state.grid.score()}")
        return state.grid.score()
    } else if (state.maxNode) {
        return maxValue(state)
    } else {
        return expectedValue(state)
    }
}

fun maxValue(state: State): Int {
    //println("Finding max of values at depth ${state.depth}")
    return listOf(Direction.LEFT, Direction.UP, Direction.RIGHT, Direction.DOWN)
            .map { direction -> state.grid.move(direction) }
            .filter { it.score() > 0 }
            .map { state.copy(it, maxNode = false) }
            .map { value(it) }
            .sortedByDescending{ it }
            .getOrElse(0, {0})
}

fun expectedValue(state: State): Int {
    //println("Computing expected value at depth ${state.depth}")

    var count = 0
    var expectedValue = 0
    for (i in 0..3) {
        for (j in 0..3) {
            val tile = state.grid.getTile(i, j)
            if (tile == 0) {
                listOf(Pair(2, 0.9), Pair(4, 0.1)).map {
                    val newGrid = state.grid.setTile(i, j, it.first)
                    expectedValue += (value(state.copy(newGrid, depth = state.depth - 1, maxNode = true)) * it.second).toInt()
                    count++
                }
            }
        }
    }
    return expectedValue / count
}

//data class State(val grid: Grid, val depth: Int, val maxNode: Boolean = true)
//
//fun getBestMove2(grid: Grid, depth: Int): Pair<Direction, Int> {
//    return Observable.just(null).flatMap {
//        Observable.just(Direction.LEFT, Direction.UP, Direction.RIGHT, Direction.DOWN)
//                .subscribeOn(Schedulers.computation())
//                .map {
//                    val movedGrid = grid.move(it)
//                    val bestScore = value(State(movedGrid, depth))
//                    Pair(it, bestScore)
//                }
//    }.toList().toBlocking().single()
//            .sortedBy { it.second }
//            .last()
//}
//
//fun value(state: State): Int {
//    //println("Finding value with appro at depth ${state.depth}")
//    if (state.depth == 0) {
//        //println("Returning terminal value: ${state.grid.score()}")
//        return state.grid.score()
//    } else if (state.maxNode) {
//        return maxValue(state)
//    } else {
//        return expectedValue(state)
//    }
//}
//
//fun maxValue(state: State): Int {
//    //println("Finding max of values at depth ${state.depth}")
//    return listOf(Direction.LEFT, Direction.UP, Direction.RIGHT, Direction.DOWN)
//            .map { direction -> state.grid.move(direction) }
//            .filter { it.score() > 0 }
//            .map { state.copy(it, maxNode = false) }
//            .map { value(it) }
//            .sortedByDescending{ it }
//            .getOrElse(0, {0})
//}
//
//fun expectedValue(state: State): Int {
//    //println("Computing expected value at depth ${state.depth}")
//    return state.grid.tiles.mapIndexed { i, tile ->
//        if (tile.value != 0) {
//            0
//        } else {
//            listOf(Pair(2, 0.9), Pair(4, 0.1)).map {
//                val listCopy = state.grid.tiles.copyOf()
//                listCopy[i] = tile.copy(value = it.first)
//                val newGrid = Grid(listCopy.toList())
//                (value(state.copy(newGrid, depth = state.depth - 1, maxNode = true)) * it.second).toInt()
//            }.average().toInt()
//        }
//    }.filter { it != 0 }.average().toInt()
//}


