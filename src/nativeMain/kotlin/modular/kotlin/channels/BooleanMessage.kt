@file:Suppress("unused")

package modular.kotlin.channels

import kotlinx.cinterop.alloc
import kotlinx.cinterop.free
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import modular.kotlin.interop.MessageData

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