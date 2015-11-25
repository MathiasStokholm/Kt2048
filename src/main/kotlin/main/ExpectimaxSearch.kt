package main

import rx.Observable
import rx.schedulers.Schedulers
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

data class State(val grid: Grid, val depth: Int, val maxNode: Boolean = false)

class ExpectimaxSearch(initialSize: Int = 450000) {
    val transpositionTable: MutableMap<Grid, Pair<Int, Int>> = ConcurrentHashMap(initialSize)
    val moves: AtomicInteger = AtomicInteger(0)
    val cacheHits: AtomicInteger = AtomicInteger(0)

    fun getBestMove(grid: Grid, depth: Int): Triple<Direction, Int, ExpectimaxSearch> {
        moves.set(4)
        return Observable.just(Direction.LEFT, Direction.UP, Direction.RIGHT, Direction.DOWN).flatMap {
            Observable.just(it).subscribeOn(Schedulers.computation())
                    .map {
                        val movedGrid = grid.move(it)
                        if (movedGrid.score() <= 0)
                            Pair(it, 0)
                        else
                            Pair(it, value(State(movedGrid, depth)))
                    }
        }.toList().toBlocking().single()
                .sortedBy { it.second }
                .takeLast(1)
                .map { Triple(it.first, it.second, this) }
                .first()
    }

    fun value(state: State): Int {
        if (state.depth == 0) {
            return state.grid.score()
        }
        if (state.maxNode) {
            return maxValue(state)
        } else {
            if (state.grid in transpositionTable) {
                val (savedScore, depth) = transpositionTable[state.grid]!!
                if (depth >= state.depth) {
                    cacheHits.andIncrement
                    return savedScore
                }
            }
            val expectedValue = expectedValue(state)
            transpositionTable.set(state.grid, Pair(expectedValue, state.depth))
            return expectedValue
        }
    }

    fun maxValue(state: State): Int {
        moves.addAndGet(4)
        return listOf(Direction.LEFT, Direction.UP, Direction.RIGHT, Direction.DOWN)
                .map { direction -> state.grid.move(direction) }
                .filter { it.score() > 0 }
                .map { state.copy(it, maxNode = false) }
                .map { value(it) }
                .sortedByDescending { it }
                .getOrElse(0, { 0 })
    }

    fun expectedValue(state: State): Int {
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