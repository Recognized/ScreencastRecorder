package com.github.recognized.screencast.recorder.format

import com.github.recognized.screencast.recorder.sound.EditionsView
import kotlinx.serialization.Serializable

@Serializable
class ScreencastZipSettings {
  private val myEntries: MutableMap<String, Any> = mutableMapOf()

  @Suppress("unchecked_cast")
  operator fun <T> get(key: Key<T>): T? {
    return myEntries[key.name] as? T
  }

  operator fun <T> set(key: Key<T>, value: T?) {
    if (value == null) {
      myEntries.remove(key.name)
    } else {
      myEntries[key.name] = value as Any
    }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is ScreencastZipSettings) return false

    if (myEntries != other.myEntries) return false

    return true
  }

  override fun hashCode(): Int {
    return myEntries.hashCode()
  }

  override fun toString(): String {
    return "ScreencastZipSettings(keys=${myEntries.keys})"
  }

  class Key<T>(val name: String)

  companion object {

    val PLUGIN_AUDIO_OFFSET_KEY = Key<Long>("PLUGIN_AUDIO_OFFSET")
    val PLUGIN_EDITIONS_VIEW_KEY = Key<EditionsView>("PLUGIN_EDITIONS_VIEW")
    val IMPORTED_AUDIO_OFFSET_KEY = Key<Long>("IMPORTED_AUDIO_OFFSET")
    val IMPORTED_EDITIONS_VIEW_KEY = Key<EditionsView>("IMPORTED_EDITIONS_VIEW")
    val SCRIPT_KEY = Key<String>("SCRIPT")
  }
}