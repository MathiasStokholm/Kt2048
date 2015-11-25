import main.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue


public class GridTest {

    @Before
    fun setUp() {
    }

    @After
    fun tearDown() {
        // tear down the test case
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
    fun testMove() {
        val grid = newInstance(listOf(Tile(0, 0, 2), Tile(0, 1, 2), Tile(0, 2, 8), Tile(0, 3, 16)))
        //grid.print()
        //grid.transpose().print().transpose().print()
        grid.print().move(Direction.DOWN).print().move(Direction.RIGHT).print().move(Direction.UP).print().move(Direction.LEFT).print()
        //grid.move(Direction.LEFT).move(Direction.RIGHT).print()

        val startTime = System.currentTimeMillis()

        var lastGrid = newInstance(listOf(Tile(0, 0, 2), Tile(1, 0, 2), Tile(2, 0, 2), Tile(3, 0, 0)))
        for (i in 0..100000) {
            lastGrid = when (i % 2) {
                0 -> lastGrid.move(Direction.RIGHT)
                else -> lastGrid.move(Direction.LEFT)
            }
        }
        println("100000 runs took: ${System.currentTimeMillis() - startTime} ms")
    }

    @Test
    fun testSetTile() {
        val grid = newInstance(listOf(Tile(0, 0, 2), Tile(0, 1, 2), Tile(0, 2, 8), Tile(0, 3, 16)))

        for (i in 0..3) {
            for (j in 0..3) {
                val tile = grid.getTile(i, j)
                if (tile == 0) {
                    val newGrid = grid.copyAndSet(i, j, 2)

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
    fun testCopyGrid() {
        val grid = newInstance(listOf(Tile(0, 0, 2), Tile(0, 1, 2), Tile(0, 2, 8), Tile(0, 3, 16)))

        val startTime = System.currentTimeMillis()
        var lastgrid = grid
        for (i in 0..100000) {
            lastgrid = lastgrid.copy(10, 100)
        }
        println("100000 runs took: ${System.currentTimeMillis() - startTime} ms")
    }

    @Test
    fun testScoreGrid() {
        val grid = newInstance(listOf(Tile(0, 0, 2), Tile(0, 1, 2), Tile(0, 2, 8), Tile(0, 3, 16)))

        val startTime = System.currentTimeMillis()
        var score = 0
        for (i in 0..100000) {
            score += grid.score()
        }
        println("100000 runs took: ${System.currentTimeMillis() - startTime} ms")
    }

    @Test
    fun testMoveRow() {
        val grid = newInstance(listOf(Tile(0, 0, 2), Tile(1, 0, 2), Tile(2, 0, 4), Tile(3, 0, 8)))

        val startTime = System.currentTimeMillis()
        for (i in 0..100000) {
            Row(grid.data1 and 0xFFFFF).move()
            Row(grid.data1 shr 20).move()
            Row(grid.data2 and 0xFFFFF).move()
            Row(grid.data2 shr 20).move()
        }
        println("100000 runs took: ${System.currentTimeMillis() - startTime} ms")
    }

    @Test
    fun testMoveRowLookup() {
        val grid = newInstance(listOf(Tile(0, 0, 2), Tile(1, 0, 2), Tile(2, 0, 4), Tile(3, 0, 8)))

        val startTime = System.currentTimeMillis()
        for (i in 0..100000) {
            moveMap[Row(grid.data1 and 0xFFFFF).data]
            moveMap[Row(grid.data1 shr 20).data]
            moveMap[Row(grid.data2 and 0xFFFFF).data]
            moveMap[Row(grid.data2 shr 20).data]
        }
        println("100000 runs took: ${System.currentTimeMillis() - startTime} ms")
    }

    @Test
    fun testScoring() {
        val grid = newInstance(listOf(Tile(0, 0, 16), Tile(1, 0, 8), Tile(2, 0, 4), Tile(3, 0, 2))).print()
        println("Score: ${grid.score()}")

        val badGrid = newInstance(listOf(Tile(0, 0, 8), Tile(1, 0, 4), Tile(2, 0, 16), Tile(3, 0, 2))).print()
        println("Score: ${badGrid.score()}")

        val verticalGrid = newInstance(listOf(Tile(0, 0, 8), Tile(0, 1, 4), Tile(0, 2, 16), Tile(0, 3, 2))).print()
        println("Score: ${verticalGrid.score()}")
    }
}