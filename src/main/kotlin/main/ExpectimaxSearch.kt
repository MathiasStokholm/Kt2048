package main

import rx.Observable
import rx.schedulers.Schedulers
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

data class State(val grid: Grid, val depth: Int, val maxNode: Boolean = false)

/**
 * Class used to conduct an Expectimax search. Construct and call getBestMove() to carry out search.
 * @param initialSize the initial size of the transposition table
 * @property transpositionTable a transposition table used to reduce redundant computation of already-seen moves. Implementation uses
 * a ConcurrentHashMap to enable parallel processing of moves
 * @property moves an AtomicInteger used to track number of attempted moves across threads
 * @property cacheHits an AtomicInteger used to track transposition table hits
 */
class ExpectimaxSearch(initialSize: Int = 450000) {
    val transpositionTable: MutableMap<Grid, Pair<Int, Int>> = ConcurrentHashMap(initialSize)
    val moves: AtomicInteger = AtomicInteger(0)
    val cacheHits: AtomicInteger = AtomicInteger(0)

    /**
     * Determines the best move to make using the Expectimax algorithm
     * @param grid the current Grid
     * @param depth the depth at which to stop the search
     * @return a Triple containing the optimal direction to move, the highest expected value encountered in the search tree
     * and a reference to the search itself
     */
    fun getBestMove(grid: Grid, depth: Int): Triple<Direction, Int, ExpectimaxSearch> {
        // Moves in all four directions will be attempted
        moves.set(4)

        // Conduct the four top-level moves on different threads from the computation thread pool. Merge the resulting scores
        // back into a list and pick the one with the highest expected score
        return Observable.just(Direction.LEFT, Direction.UP, Direction.RIGHT, Direction.DOWN).flatMap {
            Observable.just(it).subscribeOn(Schedulers.computation())
                    .map {
                        // Attempt move, if move is invalid, return a score of 0 right away
                        val movedGrid = grid.move(it)
                        if (movedGrid.score() <= 0)
                            Pair(it, 0)
                        else
                            // Move is valid, deepen search
                            Pair(it, value(State(movedGrid, depth)))
                    }
        }.toList().toBlocking().single()
                .sortedBy { it.second }
                .takeLast(1)
                .map { Triple(it.first, it.second, this) }
                .first()
    }

    /**
     * Delegates a call to one of three functions:
     *  a. If final depth is reached, return the score of the Grid reached at this state
     *  b. If node is a maximizing node, delegate to maxValue function
     *  c. If node is an expectation node, return the expected value of the current Grid. If the current Grid has been
     *     evaluated previously and stored in the transposition table, simply return the stored value
     * @return a value produced by the delegated call
     */
    private fun value(state: State): Int {
        if (state.depth == 0) {
            return state.grid.score()
        }
        if (state.maxNode) {
            return maxValue(state)
        } else {
            // Check if Grid has already been visited from another series of moves, and use stored value if so
            if (state.grid in transpositionTable) {
                val (savedScore, depth) = transpositionTable[state.grid]!!
                // Require the depth at which Grid was previously seen to be greater than or equal to current depth
                if (depth >= state.depth) {
                    cacheHits.andIncrement
                    return savedScore
                }
            }
            val expectedValue = expectedValue(state)

            // Store computed value in transposition table for later
            transpositionTable.set(state.grid, Pair(expectedValue, state.depth))
            return expectedValue
        }
    }

    /**
     * Determines the maximum score attainable by moving in either of the four directions from the given Grid
     */
    private fun maxValue(state: State): Int {
        // Four more moves will be attempted. Increment moves accordingly
        moves.addAndGet(4)

        // Carry out moves in each direction and calculate expected score of the reached Grid
        return listOf(Direction.LEFT, Direction.UP, Direction.RIGHT, Direction.DOWN)
                .map { direction -> state.grid.move(direction) }
                .filter { it.score() > 0 }
                .map { state.copy(it, maxNode = false) }
                .map { value(it) }
                .sortedByDescending { it }
                .getOrElse(0, { 0 })
    }

    /**
     * Determines the expected score of a given Grid by iteratively placing 2 and 4 tiles in every free position and
     * calling the evaluation function on each of the resulting Grids. Finally, the expected value may be calculated as
     * the weighted sum of all visited Grids divided by the number of visited Grids.
     */
    private fun expectedValue(state: State): Int {
        var count = 0
        var sum = 0
        for (i in 0..3) {
            for (j in 0..3) {
                val tile = state.grid.getTile(i, j)
                if (tile == 0) {
                    listOf(Pair(2, 0.9), Pair(4, 0.1)).map {
                        val newGrid = state.grid.copyAndSet(i, j, it.first)
                        sum += (value(state.copy(newGrid, depth = state.depth - 1, maxNode = true)) * it.second).toInt()
                        count++
                    }
                }
            }
        }
        return sum / count
    }
}