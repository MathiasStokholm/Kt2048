import main.Direction
import main.Grid
import main.NewGrid
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

        val startTime = System.currentTimeMillis()

        var lastGrid = Grid(listOf(Tile(0, 0, 2), Tile(1, 0, 2), Tile(2, 0, 2), Tile(3, 0, 0)))
        for (i in 0..100000) {
            lastGrid = when (i % 2) {
                0 -> lastGrid.move(Direction.RIGHT)
                else -> lastGrid.move(Direction.LEFT)
            }
        }
        println("100000 runs took: ${System.currentTimeMillis() - startTime} ms")
    }

    @Test
    fun testMoveNew() {
        val grid = NewGrid(listOf(Tile(0, 0, 2), Tile(1, 0, 2), Tile(2, 0, 2), Tile(3, 0, 0)))
        grid.print()

        grid.execute_move_3().print()
//        val expectedGrid = Grid(listOf(Tile(2, 0, 2), Tile(3, 0, 4)))
//        assertTrue { grid.move(Direction.RIGHT).containedIn(expectedGrid) }

        val startTime = System.currentTimeMillis()

        var lastGrid = NewGrid(listOf(Tile(0, 0, 2), Tile(1, 0, 2), Tile(2, 0, 2), Tile(3, 0, 0)))
        for (i in 0..100000) {
            lastGrid = when (i % 2) {
                0 -> lastGrid.execute_move_3()
                else -> lastGrid.execute_move_2()
            }
        }
        println("100000 runs took: ${System.currentTimeMillis() - startTime} ms")
    }
}