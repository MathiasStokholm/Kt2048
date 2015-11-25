package main

import org.openqa.selenium.By
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.remote.RemoteWebDriver

// Top level constants
val RUNS = 10
val GRID_SIZE = 4;

data class Tile(val column: Int, val row: Int, val value: Int)

enum class Direction(useColumn: Boolean, reverse: Boolean) {
    LEFT(false, true), UP(true, true),
    RIGHT(false, false), DOWN(true, false);
    val moveAlongColumn = useColumn
    val reverseNeeded = reverse
}

fun main(args: Array<String>) {
    System.setProperty("webdriver.chrome.driver","C:\\Users\\gedemis\\IdeaProjects\\Kot2048\\chromedriver.exe");

    val webDriver = ChromeDriver()
    webDriver.setup()

    val scores = (0 .. RUNS).map {
        val startTime = System.currentTimeMillis()

        val moves = runGame(webDriver)

        val score = webDriver.findElementByClassName("score-container").text
        val time = (System.currentTimeMillis() - startTime)
        println("Game over! Score: " + score + ", moves: $moves, m/s: ${moves.toFloat()*1000 / time}")
        Thread.sleep(5000)

        // Start new game
        webDriver.findElement(By.className("restart-button")).click()
        Thread.sleep(500)

        Triple(score, moves, time)
    }

    webDriver.quit()
}

tailrec fun runGame(webDriver: RemoteWebDriver, direction: Direction? = null, moves: Int = 0, gameContinued: Boolean = false): Int {
    val queryResult = webDriver.getTilesOptimized(direction)
    val continueState = if (queryResult.won && !gameContinued) {
        webDriver.continueGame()
        true
    } else false

    val currentGrid = newInstance(queryResult.tiles)
    val compStartTime = System.currentTimeMillis()

    val bestGuess = ExpectimaxSearch().getBestMove(currentGrid, 5)
    if (bestGuess.second == 0) {
        println("No directions to move! Ending game, computation took: ${System.currentTimeMillis() - compStartTime}")
        return moves
    } else {
        println("Moving in direction: ${bestGuess.first.name} with score: ${bestGuess.second}, computation took: ${System.currentTimeMillis() - compStartTime}ms, " +
                "moves considered: ${bestGuess.third.moves}, cache size: ${bestGuess.third.transpositionTable.size}, cache hits: ${bestGuess.third.cacheHits}")
        val move = bestGuess.first
        return runGame(webDriver, move, moves + 1, continueState)
    }
}
