package com.github.recognized.screencast.recorder.sound

import com.github.recognized.screencast.recorder.sound.EditionsModel.EditionType.*
import com.github.recognized.screencast.recorder.sound.impl.DefaultEditionsModel
import java.io.StringReader
import java.util.*

interface EditionsView {

  /**
   * @return Sorted by first element of pair list of editionsModel that were made.
   */
  val editionsModel: List<Pair<LongRange, EditionsModel.EditionType>>

  fun impose(frameRange: LongRange): LongRange

  fun impose(frame: Long): Long {
    return impose(frame..frame).start
  }

  fun overlay(frameRange: LongRange): LongRange

  fun overlay(frame: Long): Long {
    return overlay(frame..frame).start
  }

  fun copy(): EditionsModel

  fun serialize(): ByteArray {
    return buildString {
      for ((range, type) in editionsModel) {
        append("${range.start} ${range.endInclusive} $type\n")
      }
    }.toByteArray(Charsets.UTF_8)
  }
}

interface EditionsModel : EditionsView {
  enum class EditionType {
    CUT, MUTE, NO_CHANGES
  }

  /**
   * Cut a frame range.
   *
   * If [frameRange] intersects ranges that were muted, they become cut and not muted.
   */
  fun cut(frameRange: LongRange)

  /**
   * Mute a frame range.
   *
   * If [frameRange] intersects ranges that were cut, they become muted and not cut.
   */
  fun mute(frameRange: LongRange)

  /**
   * Undo all changes made to this [frameRange]
   */
  fun unmute(frameRange: LongRange)

  /**
   * Reset all changes.
   */
  fun reset()

  fun shift(delta: Long)

  fun load(other: EditionsView) {
    reset()
    for ((range, type) in other.editionsModel) {
      when (type) {
        CUT -> cut(range)
        MUTE -> mute(range)
        NO_CHANGES -> Unit
      }
    }
  }

  companion object {

    fun deserialize(bytes: ByteArray): EditionsModel {
      with(DefaultEditionsModel()) {
        val sc = Scanner(StringReader(String(bytes, Charsets.UTF_8)))
        while (sc.hasNext()) {
          val range = LongRange(sc.nextLong(), sc.nextLong())
          when (sc.next()) {
            CUT.name -> cut(range)
            MUTE.name -> mute(range)
            NO_CHANGES.name -> unmute(range)
          }
        }
        return this
      }
    }
  }
}