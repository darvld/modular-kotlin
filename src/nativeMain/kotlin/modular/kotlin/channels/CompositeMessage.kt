@file:Suppress("unused")

package modular.kotlin.channels

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import modular.kotlin.interop.MessageData
import kotlin.reflect.KProperty

@OptIn(ExperimentalSerializationApi::class)
/**A [Message] implementation using the [MessageData.content] field to store a ProtoBuf-encoded [MutableMap].
 *
 * Map entries can be retrieved manually by accessing the [data] field. However the recommended way is to use the
 * generic shortcuts, which automatically encode/decode the [ByteArray] values into the proper types using [ProtoBuf].
 *
 * This class implements the [getValue] and [setValue] operators, so you can use it as a property delegate:
 *
 * ```
 * val composite = CompositeMessage.decode(...)
 *
 * val age: Int by composite
 * var name: String by composite
 *
 * name = "Foo"
 * ```
 *
 * When using the delegates, keep in mind that the property name must match the Map key.
 *
 * */
public class CompositeMessage(
    code: Int,
    override val data: MutableMap<String, ByteArray>
) : Message<Map<String, ByteArray>>(code) {

    override fun encode(): MessageData = encodeObject(data, code)

    /**Retrieves the [ByteArray] associated with the given [key] in the [data] map, and decodes a [T] instance from it.*/
    public inline operator fun <reified T> get(key: String): T {
        return data[key]?.let { ProtoBuf.decodeFromByteArray(it) }
            ?: throw Exception("Field $key not found in message.")
    }

    /**Encode the given [value] and store it in the [data] map under [key].*/
    public inline operator fun <reified T> set(key: String, value: T) {
        data[key] = ProtoBuf.encodeToByteArray(value)
    }

    /**Removes the value associated with [key] and returns its decoded form.*/
    public inline fun <reified T> extract(key: String): T? {
        return data.remove(key)?.let { ProtoBuf.decodeFromByteArray(it) }
    }

    /**Removes the value associated with [key], returns true on success and false otherwise.*/
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