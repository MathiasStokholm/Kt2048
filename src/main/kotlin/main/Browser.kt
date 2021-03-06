package main

import org.openqa.selenium.*
import org.openqa.selenium.remote.RemoteWebDriver

/**
 * Initializes the browser by navigating to the correct website, resizing window and hooking into the game's JS implementation.
 * Once this function returns, the GameManager object will be exposed in the public field "GameManager._instance" and can thus
 * be accessed directly in subsequent queries.
 * @param dimension the Dimension of the browser window
 */
fun RemoteWebDriver.setup(dimension: Dimension = Dimension(400, 700)) {
    manage().window().size = dimension
    get("https://gabrielecirulli.github.io/2048/")

    // Replace the "isGameTerminated" function with a custom function.
    val script = """
                    _func_tmp = GameManager.prototype.isGameTerminated;
                    GameManager.prototype.isGameTerminated = function() {
                        GameManager._instance = this;
                        return true;
                    };
                """

    if (this is JavascriptExecutor) {
        executeScript(script)

        // Trigger the "isGameTerminated" function by sending a KeyEvent
        keyboard.pressKey(Keys.ARROW_UP)
        Thread.sleep(100)
        keyboard.releaseKey(Keys.ARROW_UP)

        // Restore original "isGameTerminated" function
        executeScript("GameManager.prototype.isGameTerminated = _func_tmp;")
    }
}

/**
 * Continue the game. Only works if the game is in the "won" state
 */
fun WebDriver.continueGame() {
    // Wait for "continue" button to appear, then click
    findElement(By.className("keep-playing-button")).click()
}


data class TileQuery(val tiles: List<Tile>, val won: Boolean)

/**
 * Main point of interacting with the game running in browser. Carries out three functions:
 *  1. Carries out a move in a supplied direction (if applicable)
 *  2. Returns whether the game is in the "won" state
 *  3. Returns the current Grid layout
 * @param direction the Direction to move, can be null if no move should be made
 */
fun RemoteWebDriver.getTilesOptimized(direction: Direction? = null): TileQuery {
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

    // Carry out a type-safe return of query results of the form: [GAME_WON, "1,1,2", "2,2,8"]
    if (returnValue is List<*>) {
        return TileQuery(returnValue.filterIsInstance<String>().map {
            val parts = it.split(",")
            Tile(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
        }, returnValue[0] as Boolean)
    }

    return TileQuery(emptyList(), false)
}