@file:Suppress("unused")

package modular.kotlin.channels

import kotlinx.cinterop.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import modular.kotlin.interop.MessageData

public fun MessageData(
    code: Int,
    size: Int = 0,
    content: COpaquePointer? = null
): MessageData = nativeHeap.alloc {
    this.code = code
    this.size = size
    this.content = content
}

public inline fun <T> MessageData.use(block: (MessageData) -> T): T {
    return block(this).also { nativeHeap.free(ptr) }
}

public abstract class Message<T>(public val code: Int) {
    public abstract val data: T
    public abstract fun encode(): MessageData

    public companion object {
        @OptIn(ExperimentalSerializationApi::class)
        public inline fun <reified T> encodeObject(data: T, code: Int): MessageData {
            val bytes = ProtoBuf.encodeToByteArray(data)

            return MessageData(code, bytes.size, nativeHeap.allocArrayOf(bytes))
        }

        @OptIn(ExperimentalSerializationApi::class)
        public inline fun <reified T> decodeObject(data: MessageData): T {
            val bytes = data.content?.readBytes(data.size)
                ?: throw Exception("Unable to decode object from message $data")

            return ProtoBuf.decodeFromByteArray<T>(bytes).also {
                nativeHeap.free(data)
            }
        }

        public inline fun encodePointer(pointer: COpaquePointer?, code: Int): MessageData =
            MessageData(code, 0, pointer)

        public inline fun <T : CPointed> decodePointer(data: MessageData): CPointer<T>? {
            return data.content?.reinterpret()
        }

        /**Create and [setup] a [CompositeMessage], and returning its encoded form.*/
        public inline fun compose(code: Int = 200, setup: CompositeMessage.() -> Unit): MessageData {
            return CompositeMessage(code, mutableMapOf()).apply(setup).encode()
        }
    }
}

public fun interface MessageInterpreter<M : Message<*>> {
    public fun decode(data: MessageData): M
}

public inline fun <M : Message<*>> MessageData.decodeAs(interpreter: MessageInterpreter<M>): M {
    return interpreter.decode(this)
}