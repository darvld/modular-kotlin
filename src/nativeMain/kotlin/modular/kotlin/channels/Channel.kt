@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package modular.kotlin.channels

import kotlinx.cinterop.*
import modular.kotlin.interop.ChannelData
import modular.kotlin.interop.ChannelEventHandler
import modular.kotlin.interop.MessageData
import kotlin.native.concurrent.SharedImmutable

public typealias MessageListener = (MessageData) -> MessageData?
public typealias ChannelHandle = CPointer<ChannelData>

@SharedImmutable
private val ChannelCallbackHandler: ChannelEventHandler =
    staticCFunction { kotlinRef: COpaquePointer?, messageData: CPointer<MessageData>? ->
        val channel = kotlinRef?.asStableRef<InputChannel>()?.get() ?: return@staticCFunction null

        messageData ?: return@staticCFunction channel.disconnect().let { null }

        channel.listener?.invoke(messageData.pointed)?.ptr
    }

public sealed class Channel(public val handle: ChannelHandle) {
    public fun handshake(): MessageData = Message.encodePointer(handle, 1)

    public companion object {
        public const val MESSAGE_PING: Int = 1

        public fun open(): OutputChannel {
            return OutputChannel(nativeHeap.alloc<ChannelData>().ptr)
        }

        public fun install(handle: ChannelHandle): InputChannel {
            return InputChannel(handle)
        }

        public fun install(handshakeMessage: MessageData): InputChannel = InputChannel(
            Message.decodePointer(handshakeMessage)
                ?: throw Exception("To install a channel using a handshake MessageData, the content pointer must point to a valid ChannelData struct.")
        )

    }
}

public class InputChannel internal constructor(handle: ChannelHandle) : Channel(handle) {
    private val stableRef = StableRef.create(this)

    init {
        handle.pointed.handler = ChannelCallbackHandler
        handle.pointed.kotlinRef = stableRef.asCPointer()
    }

    internal var listener: MessageListener? = null

    public fun unsubscribe() {
        listener = null
    }

    public fun listen(response: (MessageData) -> MessageData?): MessageListener = response.also {
        listener = it
    }

    public inline fun subscribe(crossinline callback: (MessageData) -> Unit): MessageListener = listen {
        callback(it)
        null
    }

    public inline fun <T : Message<*>> subscribe(
        interpreter: MessageInterpreter<T>,
        crossinline callback: (T) -> Unit
    ): MessageListener = listen {
        callback(interpreter.decode(it))
        null
    }

    public inline fun <T : Message<*>> listen(
        interpreter: MessageInterpreter<T>,
        crossinline response: (T) -> MessageData?
    ): MessageListener = listen {
        response(interpreter.decode(it))
    }

    /**Stops listening on this channel. If no Shutdown message is received, [disconnect] must be called manually to dispose
     * this channel's StableRef, otherwise memory leaks will happen.*/
    public fun disconnect() {
        handle.pointed.kotlinRef?.asStableRef<InputChannel>()?.let {
            if (it.get() === this)
                it.dispose()
            else
                null
        } ?: return Unit.also { stableRef.dispose() }

        // We only get passed this point if we owned the listening channel
        handle.pointed.kotlinRef = null
        handle.pointed.handler = null

        listener = null
    }
}


public class OutputChannel internal constructor(handle: ChannelHandle) : Channel(handle) {

    /**Whether this channel is still open.
     *
     * A channel stays open until [shutdown] is manually called. Note that this does *not* mean that there's a listener
     * at the other end, however it *does* mean that a listener can install this channel to listen for messages.*/
    public var open: Boolean = true
        private set

    /**Whether the other end of this channel is connected.
     *
     * A channel is considered connected if a handler has been setup. However, the other end might not be processing
     * messages even if the handler has been registered.*/
    public val connected: Boolean
        get() {
            return handle.pointed.handler != null && handle.pointed.kotlinRef != null
        }

    public inline fun sendPing(): MessageData? = send(MessageData(MESSAGE_PING))

    /**Send a [message] through the channel, note that the message might not be processed (see [open]).
     *
     * If a response is issued, it is returned.*/
    public fun send(message: MessageData): MessageData? {
        if (!open) return null

        return handle.pointed.handler?.invoke(handle.pointed.kotlinRef, message.ptr)?.pointed
    }

    /**Automatically [encode][Message.encode] the [message] and [send] it through the channel.
     *
     * If a response is issued, it is returned.*/
    public inline fun <M : Message<*>> send(message: M): MessageData? {
        return send(message.encode())
    }

    /**Shut down the channel, sending a shutdown message to the other end and then freeing the channel data struct.
     *
     * After shutdown, any attempt to send a message does nothing.*/
    public fun shutdown() {
        // The official shutdown message, this will give the listener a chance to unsubscribe and cleanup
        handle.pointed.handler?.invoke(handle.pointed.kotlinRef, null)
        nativeHeap.free(handle)

        open = false
    }
}