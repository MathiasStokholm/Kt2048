package main

import org.openqa.selenium.*
import org.openqa.selenium.chrome.ChromeDriver
import rx.Observable
import rx.schedulers.Schedulers
import java.math.BigInteger

// Top level constants
val RUNS = 10
val log2 = Math.log(2.0)

val row_left_table = Array(65536, {0})
val row_right_table = Array(65536, {0})
val col_up_table = Array(65536, {0})
val col_down_table = Array(65536, {0})
val score_table = Array(65536, {0})
val heur_score_table = Array(65536, {0})

// Heuristic scoring settings
val SCORE_LOST_PENALTY = 200000.0
val SCORE_MONOTONICITY_POWER = 4.0
val SCORE_MONOTONICITY_WEIGHT = 47.0
val SCORE_SUM_POWER = 3.5
val SCORE_SUM_WEIGHT = 11.0
val SCORE_MERGES_WEIGHT = 700.0
val SCORE_EMPTY_WEIGHT = 270.0

val ged = {
    for (row in 0..65535) {
        val line = arrayOf(
            (row shr 0) and 0xf,
            (row shr  4) and 0xf,
            (row shr  8) and 0xf,
            (row shr 12) and 0xf
        )

        // Score
        var score = 0.0f
        for (i in 0..3) {
            val rank = line[i]
            if (rank >= 2) {
                // the score is the total sum of the tile and all intermediate merged tiles
                score += (rank - 1) * (1 shl rank)
            }
        }
        score_table[row] = score.toInt()


        // Heuristic score
        var sum = 0.0
        var empty = 0
        var merges = 0
        var prev = 0
        var counter = 0
        for (i in 0..3) {
            var rank = line[i]
            sum += Math.pow(rank.toDouble(), SCORE_SUM_POWER)
            if (rank == 0) {
                empty++
            } else {
                if (prev == rank) {
                    counter++
                } else if (counter > 0) {
                    merges += 1 + counter
                    counter = 0
                }
                prev = rank
                }
            }
        if (counter > 0) {
            merges += 1 + counter
        }

        var monotonicity_left = 0.0
        var monotonicity_right = 0.0
        for (i in 1..3) {
            if (line[i-1] > line[i]) {
                monotonicity_left += Math.pow(line[i-1].toDouble(), SCORE_MONOTONICITY_POWER) - Math.pow(line[i].toDouble(), SCORE_MONOTONICITY_POWER)
            } else {
                monotonicity_right += Math.pow(line[i].toDouble(), SCORE_MONOTONICITY_POWER) - Math.pow(line[i-1].toDouble(), SCORE_MONOTONICITY_POWER)
            }
        }

        heur_score_table[row] = (SCORE_LOST_PENALTY +
                SCORE_EMPTY_WEIGHT * empty +
                SCORE_MERGES_WEIGHT * merges -
                SCORE_MONOTONICITY_WEIGHT * Math.min(monotonicity_left, monotonicity_right) - SCORE_SUM_WEIGHT * sum).toInt()

        // execute a move to the left
        var i = 0
        while (i < 4) {
            var j = i + 1
            while (j < 4) {
                if (line[j] != 0) break
                ++j
            }

            if (j == 4) break // no more tiles to the right

            if (line[i] == 0) {
                line[i] = line[j]
                line[j] = 0
                i-- // retry this entry
            } else if (line[i] == line[j]) {
                if(line[i] != 0xf) {
                    /* Pretend that 32768 + 32768 = 32768 (representational limit). */
                    line[i]++;
                }
                line[j] = 0;
            }
            i++
        }

        val result = (line[0] shl 0) or
        (line[1] shl 4) or
        (line[2] shl 8) or
        (line[3] shl 12)
        val rev_result = reverse_row(result.toLong());
        val rev_row = reverse_row(row.toLong());

        row_left_table [    row] =         row xor result;
        row_right_table[rev_row.toInt()] = rev_row.toInt() xor rev_result.toInt();
        col_up_table   [    row] =         unpack_col(row.toLong()).toInt() xor unpack_col(result.toLong()).toInt();
        col_down_table [rev_row.toInt()] = unpack_col(rev_row).toInt() xor unpack_col(rev_result).toInt();
    }
}.invoke()

fun unpack_col(row: Long): Long {
    val COL_MASK = 0x000F000F000F000FL;
    val tmp = row;
    return (tmp or (tmp shl 12) or (tmp shl 24) or (tmp shl 36)) and COL_MASK;
}

fun reverse_row(row: Long): Long {
    return (row shr 12) or ((row shr 4) and 0x00F0) or ((row shl 4) and 0x0F00) or (row shl 12);
}


//fun transpose(x: Long): Long {
//
//    val ged = BigInteger("0xF0F00F0FF0F00F0FL")
//
//    val a1 = x and 0xF0F00F0FF0F00F0FL;
//    val a2 = x and 0x0000F0F00000F0F0L;
//    val a3 = x and 0x0F0F00000F0F0000L;
//    val a = a1 or (a2 shl 12) or (a3 shr 12);
//    val b1 = a and 0xFF00FF0000FF00FFL;
//    val b2 = a and 0x00FF00FF00000000L;
//    val b3 = a and 0x00000000FF00FF00L;
//    return b1 or (b2 shr 24) or (b3 shl 24);
//}


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

data class NewGrid(val data: Long) {
    val GRID_SIZE = 4;

    constructor(tileList : List<Tile> = emptyList()) : this({
        tileList.map {
            val valueToStore = (Math.log(it.value.toDouble()) / log2 + 0.5).toLong()
            val bitPosition = (it.row * 4 + it.column) * 4
            valueToStore shl bitPosition
        }.sum()
    }.invoke())

    fun execute_move_2(): NewGrid {
        val ROW_MASK = 0xFFFFL;

        var dataCopy = data

        dataCopy = dataCopy xor ((row_left_table[((data shr 0) and ROW_MASK).toInt()]).toLong() shl 0);
        dataCopy = dataCopy xor ((row_left_table[((data shr 16) and ROW_MASK).toInt()]).toLong() shl 16);
        dataCopy = dataCopy xor ((row_left_table[((data shr 32) and ROW_MASK).toInt()]).toLong() shl 32);
        dataCopy = dataCopy xor ((row_left_table[((data shr 48) and ROW_MASK).toInt()]).toLong() shl 48);
        return copy(dataCopy);
    }

    fun execute_move_3(): NewGrid {
        val ROW_MASK = 0xFFFFL;

        var dataCopy = data

        dataCopy = dataCopy xor ((row_right_table[((data shr 0) and ROW_MASK).toInt()]).toLong() shl 0);
        dataCopy = dataCopy xor ((row_right_table[((data shr 16) and ROW_MASK).toInt()]).toLong() shl 16);
        dataCopy = dataCopy xor ((row_right_table[((data shr 32) and ROW_MASK).toInt()]).toLong() shl 32);
        dataCopy = dataCopy xor ((row_right_table[((data shr 48) and ROW_MASK).toInt()]).toLong() shl 48);
        return copy(dataCopy);
    }

    /*fun moveList(part: Int): Pair<Int, Boolean> {
        println("Moving part: $part")
        var changed = false
        for (i in (0..(GRID_SIZE - 1)).reversed()) {

        }


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
        println("Result: $part")
        return Pair(part, changed)
    }*/

    fun print() {
        var dataCopy = data
        for (i in 0..(GRID_SIZE - 1)) {
            for (j in 0..(GRID_SIZE - 1)) {
                val powerVal = dataCopy and 0xf;
                print("${if (powerVal == 0L) 0 else (1 shl powerVal.toInt())} ")
                dataCopy = dataCopy shr 4
            }
            print("\n");
        }
        print("\n");
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

    val grid = Grid(listOf(Tile(0,0,2), Tile(1,0,2), Tile(2,0,2), Tile(3,0,0)))
    println(grid)
    println()

    //val rankedMove = getBestMove2(grid, 3)
    //println(rankedMove)

    //System.exit(0)

    //println(grid.move(Direction.RIGHT))

    //val ged = recursiveMove(grid, 3).reversed()
    //println(grid.containedIn(grid))
    //println(Grid((grid.tiles + Tile(0,0,2)).asList()).containedIn(grid))
    //System.exit(0)

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

            val currentGrid = Grid(queryResult.tiles)
            val compStartTime = System.currentTimeMillis()

            val bestGuess = getBestMove2(currentGrid, 4)
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

fun JavascriptExecutor.move(direction: Direction) {
    val translatedAction = when (direction) {
        Direction.UP -> 0
        Direction.RIGHT -> 1
        Direction.DOWN  -> 2
        Direction.LEFT  -> 3
    }

    executeScript("GameManager._instance.move($translatedAction)")
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

fun WebDriver.getTiles(): List<Tile> {
    val script = """
                    var tiles = document.getElementsByClassName('tile');
                    var arr = [];
                    for (var i = 0; i < tiles.length; i++) {
                        arr.push(tiles[i].getAttribute('class'));
                    }
                    return arr
                 """

    // Execute lookup of grid elements in JS and return a list of class names that may then be parsed in Kotlin
    if (this is JavascriptExecutor) {
        val returnValue = executeScript(script)

        // Above script will always return List<String>
        if (returnValue is List<*>) {
            return returnValue.filterIsInstance<String>().map {
                val nameParts = it.split(" ")
                val value = nameParts[1].split("-")[1].toInt()
                val column = nameParts[2].split("-")[2].toInt() - 1
                val row = nameParts[2].split("-")[3].toInt() - 1
                Tile(column, row, value)
            }.sortedByDescending{ it.value }.distinctBy { it.row + 10 * it.column }
        }
    }
    return emptyList()
}

fun getBestMove(grid: Grid, depth: Int): Pair<Direction, Int> {
    return Observable.just(null).flatMap {
        Observable.just(Direction.LEFT, Direction.UP, Direction.RIGHT, Direction.DOWN)
                .subscribeOn(Schedulers.computation())
                .map {
                    val movedGrid = grid.move(it)
                    if (movedGrid.score() > 0) {
                        val result = recursiveMove(movedGrid, depth - 1)
                        Pair(it, result.mapIndexed { index, step -> step.second / (index + 1) }.sum() + movedGrid.score())
                        //Pair(it, result.sumBy { it.second } + movedGrid.score())
                        //Pair(it, movedGrid.score())
                    } else {
                        Pair(it, 0)
                    }
                }
    }.toList().toBlocking().single()
            .sortedBy { it.second }
            .last()
}

fun recursiveMove(grid: Grid, depth: Int): List<Pair<Direction, Int>> {
    if (depth == 0) {
        return listOf(listOf(Direction.LEFT, Direction.UP, Direction.RIGHT, Direction.DOWN)
                .map { Pair(it, grid.move(it).score()) }
                .sortedBy { it.second }
                .last())
    } else {
        val bestPath = listOf(Direction.LEFT, Direction.UP, Direction.RIGHT, Direction.DOWN)
                .map { Pair(it, grid.move(it)) }
                .filter { it.second.score() > 0 }
                .map { Triple(it.first, it.second.score(), recursiveMove(it.second, depth - 1)) }
                .sortedBy { it.third.map { it.second }.sum() }
                .last()

        return bestPath.third + Pair(bestPath.first, bestPath.second)
    }
}

data class State(val grid: Grid, val depth: Int, val maxNode: Boolean = true)

fun getBestMove2(grid: Grid, depth: Int): Pair<Direction, Int> {
    return Observable.just(null).flatMap {
        Observable.just(Direction.LEFT, Direction.UP, Direction.RIGHT, Direction.DOWN)
                .subscribeOn(Schedulers.computation())
                .map {
                    val movedGrid = grid.move(it)
                    val bestScore = value(State(movedGrid, depth))
                    Pair(it, bestScore)
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
    return state.grid.tiles.mapIndexed { i, tile ->
        if (tile.value != 0) {
            0
        } else {
            listOf(Pair(2, 0.9), Pair(4, 0.1)).map {
                val listCopy = state.grid.tiles.copyOf()
                listCopy[i] = tile.copy(value = it.first)
                val newGrid = Grid(listCopy.toList())
                (value(state.copy(newGrid, depth = state.depth - 1, maxNode = true)) * it.second).toInt()
            }.average().toInt()
        }
    }.filter { it != 0 }.average().toInt()
}

