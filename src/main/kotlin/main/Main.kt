package main

import org.openqa.selenium.By
import org.openqa.selenium.Dimension
import org.openqa.selenium.chrome.ChromeDriver
import rx.Observable
import rx.schedulers.Schedulers

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
    webDriver.manage().window().size = Dimension(400, 700)
    webDriver.get("https://gabrielecirulli.github.io/2048/")
    webDriver.setup()

    val firstTime = System.currentTimeMillis()
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

            val bestGuess = ExpectimaxSearch().getBestMove(currentGrid, 5)
            if (bestGuess.second == 0) {
                println("No directions to move! Ending game, computation took: ${System.currentTimeMillis() - compStartTime}")
                break
            }

            println("Moving in direction: ${bestGuess.first.name} with score: ${bestGuess.second}, computation took: ${System.currentTimeMillis() - compStartTime}ms, " +
                    "moves considered: ${bestGuess.third.moves}, cache size: ${bestGuess.third.transpositionTable.size}, cache hits: ${bestGuess.third.cacheHits}")
            move = bestGuess.first

            moves++

            if (moves == 50) {
                println("50 moves took: ${System.currentTimeMillis() - firstTime} ms")
                System.exit(0)
            }
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
