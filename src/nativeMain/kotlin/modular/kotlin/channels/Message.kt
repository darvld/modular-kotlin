@file:Suppress("unused")

package modular.kotlin.channels

import kotlinx.cinterop.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import modular.kotlin.channels.Message.Companion.encodeObject
import modular.kotlin.channels.Message.Companion.encodePointer
import modular.kotlin.interop.MessageData

/**Convenience function used to allocate a [MessageData] struct in the [nativeHeap].*/
public fun MessageData(
    code: Int,
    size: Int = 0,
    content: COpaquePointer? = null
): MessageData = nativeHeap.alloc {
    this.code = code
    this.size = size
    this.content = content
}

/**Convenience method for decoding a message using a [MessageInterpreter] interface.*/
public inline fun <M : Message<*>> MessageData.decodeAs(interpreter: MessageInterpreter<M>): M {
    return interpreter.decode(this)
}

/**Use this [MessageData] struct and dispose it after [block] returns.*/
public inline fun <T> MessageData.use(block: (MessageData) -> T): T {
    return block(this).also { nativeHeap.free(ptr) }
}

/**A Kotlin wrapper around the [MessageData] C struct.
 *
 * This class is provided as a convenience, so subclasses can interpret the underlying data as they see fit. For usage
 * examples, see [CompositeMessage] and [BooleanMessage].
 *
 * The [MessageData] struct can also be manipulated directly, as it is convenient in some cases (see [encodeObject] and
 * [encodePointer]).
 *
 * By convention, every [Message] subclass's companion object implements the [MessageInterpreter] interface, which
 * allows to call, for example, [CompositeMessage.decode], or [InputChannel]'s listen and subscribe methods in their
 * generic variants with minimal effort.
 * */
public abstract class Message<T>(public val code: Int) {

    /**An abstract piece of data, usually extracted from the underlying [MessageData] struct.*/
    public abstract val data: T

    /**Encode this high-level message into a [MessageData] struct, ready to be sent over a [Channel]. The opposite of
     * this function is found in the [MessageInterpreter] interface's [decode][MessageInterpreter.decode] method.*/
    public abstract fun encode(): MessageData

    public companion object {
        @OptIn(ExperimentalSerializationApi::class)
        /**Encode an object of type [T] into a [MessageData] struct, by using the [ProtoBuf] serialization library.*/
        public inline fun <reified T> encodeObject(data: T, code: Int): MessageData {
            val bytes = ProtoBuf.encodeToByteArray(data)

            return MessageData(code, bytes.size, nativeHeap.allocArrayOf(bytes))
        }

        @OptIn(ExperimentalSerializationApi::class)
        /**Decode an object of type [T] from a [MessageData]. The message must have been created by the [encodeObject]
         * method, or should contain a ProtoBuf-encoded instance of a [T] object in its [MessageData.content] field.*/
        public inline fun <reified T> decodeObject(data: MessageData): T {
            val bytes = data.content?.readBytes(data.size)
                ?: throw Exception("Unable to decode object from message $data")

            return ProtoBuf.decodeFromByteArray<T>(bytes).also {
                nativeHeap.free(data)
            }
        }

        /**Allocate a new [MessageData] struct and store a [pointer] in its content field. Use [decodePointer] as
         * a quick way to get it back (or access the content pointer directly).*/
        public inline fun encodePointer(pointer: COpaquePointer?, code: Int): MessageData =
            MessageData(code, 0, pointer)

        /**Retrieves the content pointer of this [MessageData] struct and performs an automatic cast.*/
        public inline fun <T : CPointed> decodePointer(data: MessageData): CPointer<T>? {
            return data.content?.reinterpret()
        }

        /**Create and [setup] a [CompositeMessage], returning its encoded form.*/
        public inline fun compose(code: Int = 200, setup: CompositeMessage.() -> Unit): MessageData {
            return CompositeMessage(code, mutableMapOf()).apply(setup).encode()
        }
    }
}