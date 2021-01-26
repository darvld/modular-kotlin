@file:Suppress("unused")

package modular.kotlin.channels

import kotlinx.cinterop.alloc
import kotlinx.cinterop.free
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import modular.kotlin.interop.MessageData

/**Convenience class used to interpret a [MessageData] struct as carrying a [Boolean] value.
 *
 * This implementation uses the [MessageData.size] field and leaves the content pointer untouched, so feel free to use
 * that, just remember not to change the size, since it may alter the [Boolean] data of the message.
 *
 * Use the companion's [decode] function to extract a [Boolean] value from a [MessageData] struct packed by this class.*/
public class BooleanMessage(public override val data: Boolean, code: Int) : Message<Boolean>(code) {
    override fun encode(): MessageData = nativeHeap.alloc {
        code = this@BooleanMessage.code
        size = if (data) 1 else 0
    }

    public companion object : MessageInterpreter<BooleanMessage> {
        override fun decode(data: MessageData): BooleanMessage {
            return BooleanMessage(data.size == 1, data.code).also { nativeHeap.free(data.ptr) }
        }
    }
}