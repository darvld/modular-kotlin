@file:Suppress("unused")

package modular.kotlin.channels

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import modular.kotlin.interop.MessageData
import kotlin.reflect.KProperty

@OptIn(ExperimentalSerializationApi::class)
public class CompositeMessage(
    code: Int,
    override val data: MutableMap<String, ByteArray>
) : Message<Map<String, ByteArray>>(code) {

    override fun encode(): MessageData = encodeObject(data, code)

    public inline operator fun <reified T> get(key: String): T {
        return data[key]?.let { ProtoBuf.decodeFromByteArray(it) }
            ?: throw Exception("Field $key not found in message.")
    }

    public inline operator fun <reified T> set(key: String, value: T) {
        data[key] = ProtoBuf.encodeToByteArray(value)
    }

    public inline fun <reified T> extract(key: String): T? {
        return data.remove(key)?.let { ProtoBuf.decodeFromByteArray(it) }
    }

    public inline fun remove(key: String): Boolean {
        return data.remove(key) != null
    }

    public inline operator fun <reified T> getValue(thisRef: Nothing?, property: KProperty<*>): T {
        return this[property.name]
    }

    public inline operator fun <reified T> setValue(thisRef: Nothing?, property: KProperty<*>, value: T) {
        this[property.name] = value
    }

    public companion object : MessageInterpreter<CompositeMessage> {
        override fun decode(data: MessageData): CompositeMessage {
            return CompositeMessage(data.code, decodeObject(data))
        }

    }
}