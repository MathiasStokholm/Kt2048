import main.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue


public class GridTest {

    @Test
    fun testRow() {
        val row = Row().set(0, encodeValue(32768)).set(2, encodeValue(2))
        assertEquals(32768, decodeValue(row[0]))
        assertEquals(2, decodeValue(row[2]))
        val clearedRow = row.clear(0)
        assertEquals(0, decodeValue(clearedRow[0]))
        assertEquals(2, decodeValue(clearedRow[2]))
        val reversedRow = row.reversed()
        assertEquals(32768, decodeValue(reversedRow[3]))
        assertEquals(2, decodeValue(reversedRow[1]))
    }

    @Test
    fun testMove() {
        val grid = newInstance(listOf(Tile(0, 0, 2), Tile(0, 1, 2), Tile(0, 2, 8), Tile(0, 3, 16)))

        // Make down move and check tiles
        val downGrid = grid.move(Direction.DOWN)
        assertEquals(encodeValue(0), downGrid.getTile(0, 0))
        assertEquals(encodeValue(4), downGrid.getTile(1, 0))
        assertEquals(encodeValue(8), downGrid.getTile(2, 0))
        assertEquals(encodeValue(16), downGrid.getTile(3, 0))

        // Make right move and check tiles
        val rightGrid = downGrid.move(Direction.RIGHT)
        assertEquals(encodeValue(0), rightGrid.getTile(0, 3))
        assertEquals(encodeValue(4), rightGrid.getTile(1, 3))
        assertEquals(encodeValue(8), rightGrid.getTile(2, 3))
        assertEquals(encodeValue(16), rightGrid.getTile(3, 3))

        // Make up move and check tiles
        val upGrid = rightGrid.move(Direction.UP)
        assertEquals(encodeValue(4), upGrid.getTile(0, 3))
        assertEquals(encodeValue(8), upGrid.getTile(1, 3))
        assertEquals(encodeValue(16), upGrid.getTile(2, 3))
        assertEquals(encodeValue(0), upGrid.getTile(3, 3))

        // Make left move and check tiles
        val leftGrid = upGrid.move(Direction.LEFT)
        assertEquals(encodeValue(4), leftGrid.getTile(0, 0))
        assertEquals(encodeValue(8), leftGrid.getTile(1, 0))
        assertEquals(encodeValue(16), leftGrid.getTile(2, 0))
        assertEquals(encodeValue(0), leftGrid.getTile(3, 0))
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
    fun testMoveRow() {
        val grid = newInstance(listOf(Tile(0, 0, 2), Tile(1, 0, 2), Tile(2, 0, 4), Tile(3, 0, 8)))

        // Check that moves correspond to lookups in move map
        assertTrue { Row(grid.data1 and 0xFFFFF).move() == moveMap[Row(grid.data1 and 0xFFFFF).data] }
        assertTrue { Row(grid.data1 shr 20).move() == moveMap[Row(grid.data1 shr 20).data] }
        assertTrue { Row(grid.data2 and 0xFFFFF).move() == moveMap[Row(grid.data2 and 0xFFFFF).data] }
        assertTrue { Row(grid.data2 shr 20).move() == moveMap[Row(grid.data2 shr 20).data] }
    }

    @Test
    fun testScoring() {
        val grid = newInstance(listOf(Tile(0, 0, 16), Tile(1, 0, 8), Tile(2, 0, 4), Tile(3, 0, 2)))
        val badGrid = newInstance(listOf(Tile(0, 0, 8), Tile(1, 0, 4), Tile(2, 0, 16), Tile(3, 0, 2)))
        assertTrue { badGrid.score() < grid.score() }
    }
}