package main

import org.openqa.selenium.By
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.firefox.FirefoxDriver
import org.openqa.selenium.remote.RemoteWebDriver

// Top level constants
val RUNS = 10
val GRID_SIZE = 4;

data class Tile(val column: Int, val row: Int, val value: Int)

/**
 * Represents movements: LEFT, UP, RIGHT and DOWN.
 * Utility class used to provide information about how to restructure data before and after attempting a move
 */
enum class Direction(useColumn: Boolean, reverse: Boolean) {
    LEFT(false, true), UP(true, true),
    RIGHT(false, false), DOWN(true, false);
    val moveAlongColumn = useColumn
    val reverseNeeded = reverse
}

fun main(args: Array<String>) {
    // Check if user needs help
    if (args.isEmpty() || args[0].contains("help")) {
        System.out.println("Start Kt2048 by supplying a driver name and a driver binary location (chrome only)\ne.g.: \"chrome\" \"C:\\chromedriver.exe\"")
        return
    }

    // Parse driver type and binary location
    val webDriver = when (args[0]) {
        "chrome" -> {
            if (args[1].isNotBlank()) {
                System.out.println("Starting Kt2048 using Chrome")
                System.setProperty("webdriver.chrome.driver", args[1]);
                ChromeDriver()
            } else {
                System.err.println("Missing or erroneous driver path designation: ${args[1]}")
                return
            }
        }
        else -> {
            // Fall back to a firefox driver (included in Selenium and thus requires no additional binary except the browser)
            System.out.println("Starting Kt2048 using Firefox")
            FirefoxDriver()
        }
    }

    // Run the setup script that hooks the website's JS program
    webDriver.setup()

    // Attempt the game a number of times
    val scores = (0..RUNS).map {
        // Record starting time of this play-through
        val startTime = System.currentTimeMillis()

        // Play the game! Game will be over when this function returns
        val moves = runGame(webDriver)

        // Find the final score
        val score = webDriver.findElementByClassName("score-container").text

        // Record end time of this play-through
        val time = (System.currentTimeMillis() - startTime)

        println("Game over! Score: " + score + ", moves: $moves, m/s: ${moves.toFloat() * 1000 / time}")
        Thread.sleep(5000)

        // Start new game
        webDriver.findElement(By.className("restart-button")).click()
        Thread.sleep(500)

        // Store information about this play-through for analysis/fun
        Triple(score, moves, time)
    }

    println("Completed all play-throughs! Best score was: ${scores.maxBy { it.first.toInt() }?.first }")
    webDriver.quit()
}

/**
 * Plays a single game by recursively calling this function until no more moves are possible. The procedure can be outlined as:
 *  1. If the direction argument is non-null, carry out a move in the provided direction
 *  2. Obtain list of current tiles from browser
 *  3. Determine whether game is in a "won" state (the 2048 tile has just appeared) and handle it appropriately
 *  4. Construct a Grid from the current set of tiles
 *  5. Conduct an Expectimax search
 *  6a. If no moves are possible, the game has ended. Return the number of moves reached in this play-through
 *  6b. If an optimal move exists, call self with move direction and incremented move count
 * @param webDriver the WebDriver to use for interacting with browser
 * @param direction direction to move before evaluating board (can be null)
 * @param moves number of moves used to get to current state
 * @param gameContinued boolean indicating whether the game was previously resumed after reaching the "won" state (2048 tile)
 */
tailrec fun runGame(webDriver: RemoteWebDriver, direction: Direction? = null, moves: Int = 0, gameContinued: Boolean = false): Int {
    // Carry out move if applicable and return information about whether game is in the "won" state, and what the current
    // board looks like
    val queryResult = webDriver.getTilesOptimized(direction)

    // If game is in the "won" state and wasn't previously continued, continue it now
    val continueState = if (queryResult.won && !gameContinued) {
        webDriver.continueGame()
        true
    } else gameContinued

    // Construct Grid from tiles and record start time of computation
    val currentGrid = newInstance(queryResult.tiles)
    val compStartTime = System.currentTimeMillis()

    // Carry out Expectimax search
    val bestGuess = ExpectimaxSearch().getBestMove(currentGrid, 5)

    // If best score is 0, no more moves are possible and game has ended
    if (bestGuess.second == 0) {
        println("No directions to move! Ending game, computation took: ${System.currentTimeMillis() - compStartTime}")
        return moves
    } else {
        // A best move was found
        println("Moving in direction: ${bestGuess.first.name} with score: ${bestGuess.second}, computation took: ${System.currentTimeMillis() - compStartTime}ms, " +
                "moves considered: ${bestGuess.third.moves}, cache size: ${bestGuess.third.transpositionTable.size}, cache hits: ${bestGuess.third.cacheHits}")
        val move = bestGuess.first
        return runGame(webDriver, move, moves + 1, continueState)
    }
}
