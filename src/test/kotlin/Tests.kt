import main.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
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

//        var lastGrid = Grid(listOf(Tile(0, 0, 2), Tile(1, 0, 2), Tile(2, 0, 2), Tile(3, 0, 0)))
//        for (i in 0..100000) {
//            lastGrid = when (i % 2) {
//                0 -> lastGrid.move(Direction.RIGHT)
//                else -> lastGrid.move(Direction.LEFT)
//            }
//        }
//        println("100000 runs took: ${System.currentTimeMillis() - startTime} ms")
    }

    @Test
    fun testCopyGrid() {
        val grid = Grid(listOf(Tile(0, 0, 2), Tile(0, 1, 2), Tile(0, 2, 8), Tile(0, 3, 16)))

        val startTime = System.currentTimeMillis()
        var lastgrid = grid
        for (i in 0..100000) {
            lastgrid = lastgrid.copy(lastgrid.tiles.toList())
        }
        println("100000 runs took: ${System.currentTimeMillis() - startTime} ms")
    }

    @Test
    fun testRow() {
        val row = Row().set(0, encodeValue(32768)).set(2, encodeValue(2))
        println(row)
        assertEquals(32768, decodeValue(row[0]))
        assertEquals(2, decodeValue(row[2]))
        val clearedRow = row.clear(0)
        assertEquals(0, decodeValue(clearedRow[0]))
        assertEquals(2, decodeValue(clearedRow[2]))
        println(clearedRow)
        val reversedRow = row.reversed()
        assertEquals(32768, decodeValue(reversedRow[3]))
        assertEquals(2, decodeValue(reversedRow[1]))
        println(reversedRow)
    }

    @Test
    fun testMoveNew() {
        val grid = newInstance(listOf(Tile(0, 0, 2), Tile(0, 1, 2), Tile(0, 2, 8), Tile(0, 3, 16)))
        //grid.print()
        //grid.transpose().print().transpose().print()
        grid.print().move(Direction.DOWN).print().move(Direction.RIGHT).print().move(Direction.UP).print().move(Direction.LEFT).print()
        //grid.move(Direction.LEFT).move(Direction.RIGHT).print()

//        val startTime = System.currentTimeMillis()
//
//        var lastGrid = newInstance(listOf(Tile(0, 0, 2), Tile(1, 0, 2), Tile(2, 0, 2), Tile(3, 0, 0)))
//        for (i in 0..100000) {
//            lastGrid = when (i % 2) {
//                0 -> lastGrid.move(Direction.RIGHT)
//                else -> lastGrid.move(Direction.LEFT)
//            }
//        }
//        println("100000 runs took: ${System.currentTimeMillis() - startTime} ms")
    }

    @Test
    fun testSetTile() {
        val grid = newInstance(listOf(Tile(0, 0, 2), Tile(0, 1, 2), Tile(0, 2, 8), Tile(0, 3, 16)))

        for (i in 0..3) {
            for (j in 0..3) {
                val tile = grid.getTile(i, j)
                if (tile == 0) {
                    val newGrid = grid.setTile(i, j, 2)

                    for (h in 0..3) {
                        for (k in 0..3) {
                            assertTrue("Error, value: ${newGrid.getTile(h, k)} + board:\n${newGrid}", {newGrid.getTile(h, k) < 5} )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun testCopyNewGrid() {
        val grid = newInstance(listOf(Tile(0, 0, 2), Tile(0, 1, 2), Tile(0, 2, 8), Tile(0, 3, 16)))

        val startTime = System.currentTimeMillis()
        var lastgrid = grid
        for (i in 0..100000) {
            lastgrid = lastgrid.copy(10, 100)
        }
        println("100000 runs took: ${System.currentTimeMillis() - startTime} ms")
    }
}