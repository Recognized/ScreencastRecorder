package com.github.recognized.screencast.recorder.sound.impl

import com.github.recognized.kotlin.ranges.extensions.length
import com.github.recognized.kotlin.ranges.union.LongRangeUnion
import com.github.recognized.screencast.recorder.sound.EditionsModel
import com.github.recognized.screencast.recorder.sound.EditionsModel.EditionType.*


class DefaultEditionsModel : EditionsModel {
  private val myCutRanges = LongRangeUnion()
  private val myMuteRanges = LongRangeUnion()
  private val myNoChangesRanges = LongRangeUnion()
  private var myEditionsModelCache: List<Pair<LongRange, EditionsModel.EditionType>>? = null
  override val editionsModel: List<Pair<LongRange, EditionsModel.EditionType>>
    get() {
      myEditionsModelCache?.let { return it }
      val ret: MutableList<Pair<LongRange, EditionsModel.EditionType>> = mutableListOf()
      ret.addAll(myCutRanges.ranges.map { it to CUT })
      ret.addAll(myMuteRanges.ranges.map { it to MUTE })
      ret.addAll(myNoChangesRanges.ranges.map { it to NO_CHANGES })
      ret.sortBy { (a, _) -> a.start }
      return ret.toList().also { myEditionsModelCache = it }
    }

  init {
    // 256 is a maximum frame size in bytes
    myNoChangesRanges.union(LongRange(0, Long.MAX_VALUE / 256))
  }

  override fun cut(frameRange: LongRange) {
    myCutRanges.union(frameRange)
    myMuteRanges.exclude(frameRange)
    myNoChangesRanges.exclude(frameRange)
    myEditionsModelCache = null
  }

  override fun mute(frameRange: LongRange) {
    val temp = LongRangeUnion().apply { union(frameRange) }
    for (range in myCutRanges.intersection(frameRange)) {
      temp.exclude(range)
    }
    myNoChangesRanges.exclude(frameRange)
    for (range in temp.ranges) {
      myMuteRanges.union(range)
    }
    myEditionsModelCache = null
  }

  override fun unmute(frameRange: LongRange) {
    val temp = LongRangeUnion().apply { union(frameRange) }
    for (range in myCutRanges.intersection(frameRange)) {
      temp.exclude(range)
    }
    myMuteRanges.exclude(frameRange)
    for (range in temp.ranges) {
      myNoChangesRanges.union(frameRange)
    }
    myEditionsModelCache = null
  }

  override fun reset() {
    val infinity = LongRange(0, Long.MAX_VALUE / 256)
    myCutRanges.exclude(infinity)
    myMuteRanges.exclude(infinity)
    myNoChangesRanges.union(infinity)
    myEditionsModelCache = null
  }

  override fun impose(frameRange: LongRange): LongRange {
    return myCutRanges.impose(frameRange)
  }

  override fun overlay(frameRange: LongRange): LongRange {
    var start = frameRange.start
    var end = frameRange.endInclusive
    for (cut in myCutRanges.ranges) {
      if (cut.start <= start) {
        start += cut.length
      }
      if (cut.start <= end) {
        end += cut.length
      }
    }
    return start..end
  }

  override fun copy(): EditionsModel {
    val copy = DefaultEditionsModel()
    for (range in myCutRanges.ranges) {
      copy.cut(range)
    }
    for (range in myMuteRanges.ranges) {
      copy.mute(range)
    }
    return copy
  }

  override fun shift(delta: Long) {
    myCutRanges.shift(delta)
    myMuteRanges.shift(delta)
    myNoChangesRanges.shift(delta)
    myEditionsModelCache = null
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as DefaultEditionsModel

    if (myCutRanges != other.myCutRanges) return false
    if (myMuteRanges != other.myMuteRanges) return false
    if (myNoChangesRanges != other.myNoChangesRanges) return false

    return true
  }

  override fun hashCode(): Int {
    var result = myCutRanges.hashCode()
    result = 31 * result + myMuteRanges.hashCode()
    result = 31 * result + myNoChangesRanges.hashCode()
    return result
  }

  override fun toString(): String {
    return editionsModel.joinToString(separator = ", ", prefix = "[", postfix = "]") { (range, type) ->
      "$range: $type"
    }
  }
}
