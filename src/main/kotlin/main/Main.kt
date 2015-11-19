package main

import com.google.common.primitives.Longs
import org.openqa.selenium.*
import org.openqa.selenium.chrome.ChromeDriver
import rx.Observable
import rx.schedulers.Schedulers
import java.util.*

// Top level constants
val RUNS = 10
val log2 = Math.log(2.0)
val GRID_SIZE = 4;

// Construct maps of all possible moves (of a single row from left to right)
val moveMap = (0..923521).asSequence().map { Row(it) }.toMap({it}, { it.move() })
val moveMapReversed = (0..923521).asSequence().map { Row(it) }.toMap({it}, {
    val (result, success) = it.reversed().move()
    Pair(result.reversed(), success)
})

data class Tile(val column: Int, val row: Int, val value: Int)

enum class Direction(useColumn: Boolean, reverse: Boolean) {
    LEFT(false, true), UP(true, true),
    RIGHT(false, false), DOWN(true, false);
    val moveAlongColumn = useColumn
    val reverseNeeded = reverse
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

        //println("Result: $dataCopy")
        return Pair(dataCopy, changed)
    }

    override fun toString(): String {
        val builder = StringBuilder()
        (0..3).map { builder.append("${get(it)} ") }
        return builder.toString()
    }
}

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

data class Grid(val data1: Long, val data2: Long) {
    fun move(direction: Direction): Grid {
        // Check if we need to transpose the grid before moving rows
        val (component1, component2) = if (!direction.moveAlongColumn) Pair(data1, data2) else {
            val transposedGrid = transpose()
            Pair(transposedGrid.data1, transposedGrid.data2)
        }

        // Extract data into Rows
        val partialData = listOf(Row(component1 and 0xFFFFF), Row((component1 shr 20) and 0xFFFFF), Row(component2 and 0xFFFFF), Row((component2 shr 20) and 0xFFFFF))

        // Lookup moves or reversed moves based on direction
        val finalRows = partialData.map { if (!direction.reverseNeeded) moveMap[it]!! else moveMapReversed[it]!! }

         // Check whether move is valid
        val validMove = finalRows[0].second or finalRows[1].second or finalRows[2].second or finalRows[3].second
        return if (validMove) {
            val newGrid = Grid(finalRows[0].first.data.toLong() or (finalRows[1].first.data.toLong() shl 20),
                    finalRows[2].first.data.toLong() or (finalRows[3].first.data.toLong() shl 20))
            if (!direction.moveAlongColumn) {
                newGrid
            } else {
                // Transpose to move back to original coordinate system
                newGrid.transpose()
            }
        } else {
            // An invalid move will result in an empty grid with score 0
            Grid(0, 0)
        }
    }

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

    fun transpose(): Grid {
        val newData1 =  getTile(data1, 0) + (getTile(data1, 4) shl 5) + (getTile(data2, 0) shl 10) + (getTile(data2, 4) shl 15) +
                (getTile(data1, 1) shl 20) + (getTile(data1, 5) shl 25) + (getTile(data2, 1) shl 30) + (getTile(data2, 5) shl 35)
        val newData2 =  getTile(data1, 2) + (getTile(data1, 6) shl 5) + (getTile(data2, 2) shl 10) + (getTile(data2, 6) shl 15) +
                (getTile(data1, 3) shl 20) + (getTile(data1, 7) shl 25) + (getTile(data2, 3) shl 30) + (getTile(data2, 7) shl 35)
        return Grid(newData1, newData2)
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

        return score + if (score > 0) 20000 else 0
    }

    fun print(): Grid {
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

            val bestGuess = getBestMove(currentGrid, 5)
            if (bestGuess.second == 0) {
                println("No directions to move! Ending game, computation took: ${System.currentTimeMillis() - compStartTime}")
                break
            }

            println("Moving in direction: ${bestGuess.first.name} with score: ${bestGuess.second}, computation took: ${System.currentTimeMillis() - compStartTime}ms, " +
                    "moves considered: ${bestGuess.third.moves}, cache size: ${bestGuess.third.transpositionTable.size}, cache hits: ${bestGuess.third.cacheHits}")
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

data class State(val grid: Grid, val depth: Int, val maxNode: Boolean = false)
data class SearchEngine(val transpositionTable: HashMap<Grid, Pair<Int, Int>> = HashMap<Grid, Pair<Int, Int>>(250000), var moves: Int = 0, var cacheHits: Int = 0)
var engine = SearchEngine()

fun getBestMove(grid: Grid, depth: Int): Triple<Direction, Int, SearchEngine> {
    engine = SearchEngine(moves = 4)
    return Observable.just(null).flatMap {
        Observable.just(Direction.LEFT, Direction.UP, Direction.RIGHT, Direction.DOWN)
                .map {
                    val movedGrid = grid.move(it)
                    if (movedGrid.score() <= 0) Pair(it, 0) else {
                        val bestScore = value(State(movedGrid, depth))
                        if (bestScore <= 0) Pair(it, movedGrid.score()) else Pair(it, bestScore)
                    }
                }
    }.toList().toBlocking().single()
            .sortedBy { it.second }
            .takeLast(1)
            .map { Triple(it.first, it.second, engine) }
            .first()
}

fun value(state: State): Int {
    //println("Finding value with appro at depth ${state.depth}")
    if (state.depth == 0) {
        //println("Returning terminal value: ${state.grid.score()}")
        return state.grid.score()
    }
    if (state.maxNode) {
        return maxValue(state)
    } else {
        if (state.grid in engine.transpositionTable) {
            val (savedScore, depth) = engine.transpositionTable[state.grid]!!
            if (depth >= state.depth) {
                engine.cacheHits++
                return savedScore
            }
        }
        val expectedValue = expectedValue(state)
        engine.transpositionTable.set(state.grid, Pair(expectedValue, state.depth))
        return expectedValue
    }
}

fun maxValue(state: State): Int {
    engine.moves += 4
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
    var sum = 0
    for (i in 0..3) {
        for (j in 0..3) {
            val tile = state.grid.getTile(i, j)
            if (tile == 0) {
                listOf(Pair(2, 0.9), Pair(4, 0.1)).map {
                    val newGrid = state.grid.copyAndSet(i, j, it.first)
                    sum += (value(state.copy(newGrid, depth = state.depth - 1, maxNode = true)) * it.second).toInt()
                    count++
                }
            }
        }
    }
    return sum / count
}
