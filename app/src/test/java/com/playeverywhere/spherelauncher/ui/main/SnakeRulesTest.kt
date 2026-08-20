package com.playeverywhere.spherelauncher.ui.main

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SnakeRulesTest {
    @Test
    fun `step inside board returns next cell`() {
        assertEquals(Pair(6, 5), nextSnakeHead(Pair(5, 5), Pair(1, 0), 12))
    }

    @Test
    fun `left wall collision ends movement`() {
        assertNull(nextSnakeHead(Pair(0, 5), Pair(-1, 0), 12))
    }

    @Test
    fun `right wall collision ends movement`() {
        assertNull(nextSnakeHead(Pair(11, 5), Pair(1, 0), 12))
    }

    @Test
    fun `top wall collision ends movement`() {
        assertNull(nextSnakeHead(Pair(5, 0), Pair(0, -1), 12))
    }

    @Test
    fun `bottom wall collision ends movement`() {
        assertNull(nextSnakeHead(Pair(5, 11), Pair(0, 1), 12))
    }
}
