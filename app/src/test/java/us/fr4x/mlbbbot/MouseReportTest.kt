package us.fr4x.mlbbbot

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MouseReportTest {

    @Test
    fun buildEncodesButtonsAndSignedDeltas() {
        assertArrayEquals(byteArrayOf(1, 10, (-5).toByte(), 0), MouseReport.build(MouseReport.BTN_LEFT, 10, -5))
    }

    @Test
    fun buildClampsToSignedByteRange() {
        assertArrayEquals(byteArrayOf(0, 127, (-127).toByte(), 0), MouseReport.build(0, 500, -500))
    }

    @Test
    fun buildMasksUnusedButtonBits() {
        assertEquals(7, MouseReport.build(0xFF, 0, 0)[0].toInt())
    }

    @Test
    fun splitMoveSumsToTotalAndRespectsMax() {
        val cases = listOf(300 to -50, -1000 to 777, 5 to 0, 0 to -1, 127 to 127, -128 to 0)
        for ((dx, dy) in cases) {
            val steps = MouseReport.splitMove(dx, dy)
            assertEquals(dx, steps.sumOf { it.first })
            assertEquals(dy, steps.sumOf { it.second })
            assertTrue(steps.all { Math.abs(it.first) <= 127 && Math.abs(it.second) <= 127 })
        }
    }

    @Test
    fun splitMoveSmallMaxStep() {
        val steps = MouseReport.splitMove(250, -30, 4)
        assertEquals(250, steps.sumOf { it.first })
        assertEquals(-30, steps.sumOf { it.second })
        assertTrue(steps.all { Math.abs(it.first) <= 4 && Math.abs(it.second) <= 4 })
    }

    @Test
    fun splitMoveZeroIsEmpty() {
        assertTrue(MouseReport.splitMove(0, 0).isEmpty())
    }

    @Test
    fun descriptorIsNonEmptyAndBalanced() {
        val d = MouseReport.DESCRIPTOR
        assertEquals(52, d.size)
        assertEquals(0xC0.toByte(), d[d.size - 1])
    }
}
