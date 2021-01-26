@file:Suppress("unused")

package modular.kotlin

import modular.kotlin.channels.Channel
import modular.kotlin.channels.ChannelHandle
import modular.kotlin.channels.InputChannel
import modular.kotlin.interop.MessageData

public abstract class Module(private val eventsChannel: InputChannel) {
    init {
        eventsChannel.listen {
            if (it.code == -1) {
                unload()
                return@listen null
            }
            handleMessage(it)
        }
    }

    protected inline fun handle(message: MessageData, block: (MessageData) -> Unit): MessageData? {
        block(message)
        return null
    }

    protected abstract fun load(): Boolean

    protected abstract fun handleMessage(message: MessageData): MessageData?

    protected open fun unload() {
        eventsChannel.disconnect()
    }

    public companion object {
        public inline fun <T : Module> install(handle: ChannelHandle, factory: (InputChannel) -> T): T {
            return factory(Channel.install(handle))
        }
    }
}