package com.github.recognized.screencast.recorder.sound.impl

import com.github.recognized.screencast.recorder.sound.EditionsModel
import com.github.recognized.screencast.recorder.sound.EditionsModel.EditionType.*
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultEditionModelTest {
    
    @Test
    fun `test equals method`() {
        assertEquals(DefaultEditionsModel(), DefaultEditionsModel())
        val range1 = 10L..20L
        assertEquals(DefaultEditionsModel().apply { cut(range1) }, DefaultEditionsModel().apply { cut(range1) })
        assertEquals(DefaultEditionsModel().apply { mute(range1) }, DefaultEditionsModel().apply { mute(range1) })
        assertEquals(DefaultEditionsModel().apply { mute(range1); unmute(range1) }, DefaultEditionsModel())
        assertEquals(
                DefaultEditionsModel().apply { mute(range1); cut(range1) },
                DefaultEditionsModel().apply { cut(range1) }
        )
    }
    
    @Test
    fun `test cut is not affected`() {
        val model1 = DefaultEditionsModel().apply {
            cut(100..200L)
            cut(300..400L)
            cut(500..600L)
        }
        val model2 = model1.copy().apply {
            mute(50..300L)
            unmute(350..360L)
            mute(400..1000L)
            unmute(380..550L)
        }
        assertEquals(
                model1.editionsModel.filter { it.second == CUT }.map { it.first },
                model2.editionsModel.filter { it.second == CUT }.map { it.first }
        )
    }
    
    @Test
    fun `test ranges are correct`() {
        val ranges = DefaultEditionsModel().apply {
            cut(100..200L)
            cut(300..400L)
            cut(500..600L)
            mute(50..250L)
            mute(450..1000L)
        }.editionsModel
        val expectedRanges = listOf(
                0..49L to NO_CHANGES,
                50..99L to MUTE,
                100..200L to CUT,
                201..250L to MUTE,
                251..299L to NO_CHANGES,
                300..400L to CUT,
                401..449L to NO_CHANGES,
                450..499L to MUTE,
                500..600L to CUT,
                601..1000L to MUTE
        )
        assertEquals(expectedRanges, ranges.dropLast(1))
    }
    
    @Test
    fun `test serialization`() {
        val model = DefaultEditionsModel().apply {
            cut(0L..200L)
            cut(400L..600L)
            cut(800L..900L)
            mute(1000L..1200L)
            mute(150L..450L)
            mute(10000L..40000L)
            unmute(12000L..35000L)
        }
        assertEquals(model, EditionsModel.deserialize(model.serialize()))
    }
}