package com.github.recognized.screencast.recorder.format

import com.github.recognized.screencast.recorder.sound.EditionsModel
import com.github.recognized.screencast.recorder.sound.EditionsView
import com.github.recognized.screencast.recorder.sound.impl.DefaultEditionsModel
import java.util.*
import javax.xml.bind.annotation.XmlElement
import javax.xml.bind.annotation.adapters.XmlAdapter
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter

class ScreencastZipSettings {
    @field:XmlJavaTypeAdapter(MapAdapter::class)
    private val myEntries: MutableMap<String, String> = mutableMapOf()
    
    operator fun <T> get(key: Key<T>): T? {
        return myEntries[key.name]?.let { key.deserialize(it) }
    }
    
    operator fun <T> set(key: Key<T>, value: T?) {
        if (value == null) {
            myEntries.remove(key.name)
        } else {
            myEntries[key.name] = key.serialize(value)
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
    
    private class MapElements(
            @XmlElement
            var key: String,
            @XmlElement
            var value: Any
    ) {
        
        private constructor() : this("", "") //Required by JAXB
    }
    
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
    
    abstract class Key<T>(val name: String) {
        abstract fun serialize(value: T): String
        abstract fun deserialize(obj: String): T
    }
    
    class LongKey(name: String) : Key<Long>(name) {
        override fun serialize(value: Long): String {
            return value.toString()
        }
        
        override fun deserialize(obj: String): Long {
            return obj.toLong()
        }
    }
    
    class EditionsViewKey(name: String) : Key<EditionsView>(name) {
        override fun deserialize(obj: String): DefaultEditionsModel {
            return EditionsModel.deserialize(Base64.getDecoder().decode(obj)) as DefaultEditionsModel
        }
        
        override fun serialize(value: EditionsView): String {
            return Base64.getEncoder().encodeToString(value.serialize())
        }
    }
    
    class StringKey(name: String) : Key<String>(name) {
        override fun serialize(value: String): String = value
        
        override fun deserialize(obj: String): String = obj
    }
    
    class DoubleKey(name: String) : Key<Double>(name) {
        override fun serialize(value: Double): String {
            return value.toString()
        }
        
        override fun deserialize(obj: String): Double {
            return obj.toDouble()
        }
    }
    
    companion object {
        
        val PLUGIN_AUDIO_OFFSET_KEY = LongKey("PLUGIN_AUDIO_OFFSET")
        val PLUGIN_EDITIONS_VIEW_KEY = EditionsViewKey("PLUGIN_EDITIONS_VIEW")
        val IMPORTED_AUDIO_OFFSET_KEY = LongKey("IMPORTED_AUDIO_OFFSET")
        val IMPORTED_EDITIONS_VIEW_KEY = EditionsViewKey("IMPORTED_EDITIONS_VIEW")
        val SCRIPT_KEY = StringKey("SCRIPT")
        val TOTAL_LENGTH_PLUGIN = LongKey("TOTAL_LENGTH_PLUGIN")
        val TOTAL_LENGTH_IMPORTED = LongKey("TOTAL_LENGTH_IMPORTED")
    }
}