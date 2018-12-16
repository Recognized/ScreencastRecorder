package com.github.recognized.screencast.recorder.format

import com.github.recognized.screencast.recorder.sound.EditionsView
import javax.xml.bind.annotation.XmlElement
import javax.xml.bind.annotation.adapters.XmlAdapter
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter

class ScreencastZipSettings {
  @field:XmlJavaTypeAdapter(MapAdapter::class)
  private val myEntries: MutableMap<String, Any> = mutableMapOf()

  private class MapAdapter : XmlAdapter<Array<MapElements>, Map<String, Any>>() {
    override fun marshal(map: Map<String, Any>): Array<MapElements> {
      return map.map { (a, b) -> MapElements(a, b) }.toTypedArray()
    }

    override fun unmarshal(array: Array<MapElements>): Map<String, Any> {
      val map = mutableMapOf<String, Any>()
      array.forEach { map[it.key] = it.value }
      return map
    }
  }

  @Suppress("unchecked_cast")
  operator fun <T> get(key: Key<T>): T? {
    return myEntries[key.name] as? T
  }

  operator fun <T> set(key: Key<T>, value: T?) {
    myEntries[key.name] = value as Any
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

  private class MapElements(
    @XmlElement
    var key: String,
    @XmlElement
    var value: Any
  ) {

    private constructor() : this("", "") //Required by JAXB
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