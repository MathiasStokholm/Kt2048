import main.Direction
import main.Grid
import main.Tile
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertTrue


public class GridTest {

    @Before
    fun setUp() {
        // set up the test case
    }

    @After
    fun tearDown() {
        // tear down the test case
    }

    @Test
    fun testMove() {
        val grid = Grid(listOf(Tile(0, 0, 2), Tile(1, 0, 2), Tile(2, 0, 2), Tile(3, 0, 0)))
        val expectedGrid = Grid(listOf(Tile(2, 0, 2), Tile(3, 0, 4)))
        assertTrue { grid.move(Direction.RIGHT).containedIn(expectedGrid) }
    }
}