@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package modular.kotlin.channels

import kotlinx.cinterop.*
import modular.kotlin.interop.ChannelData
import modular.kotlin.interop.ChannelEventHandler
import modular.kotlin.interop.MessageData
import kotlin.native.concurrent.SharedImmutable

/**Shorthand for a function used to process messages and optionally respond to them when subscribing to a channel.*/
public typealias MessageListener = (MessageData) -> MessageData?
/**A pointer to a [ChannelData] struct.*/
public typealias ChannelHandle = CPointer<ChannelData>

@SharedImmutable
private val ChannelCallbackHandler: ChannelEventHandler =
    staticCFunction { kotlinRef: COpaquePointer?, messageData: CPointer<MessageData>? ->
        val channel = kotlinRef?.asStableRef<InputChannel>()?.get() ?: return@staticCFunction null

        messageData ?: return@staticCFunction channel.disconnect().let { null }

        channel.listener?.invoke(messageData.pointed)?.ptr
    }

/**Abstract representation of a communication channel, used to connect with runtime loaded modules.
 *
 *  Actual implementation is handled by [InputChannel] and [OutputChannel].*/
public sealed class Channel(public val handle: ChannelHandle) {
    /**Provides a [MessageData] struct containing the necessary information to [install] an [InputChannel]. The
     *  purpose of this function is to provide a portable way to send a handshake using a loaded method invocation,
     *  so the target module can then install the channel and subscribe to it.*/
    public fun handshake(): MessageData = Message.encodePointer(handle, 1)

    public companion object {
        public const val MESSAGE_PING: Int = 1

        /**Opens a new [Channel] by allocating a [ChannelData] struct. The struct is *not* initialized, since this
         *  should be handled by the receiving end upon channel installation. Use the returned channel's [handshake]
         *  method to obtain a message you can use to establish a connection.*/
        public fun open(): OutputChannel {
            return OutputChannel(nativeHeap.alloc<ChannelData>().ptr)
        }

        /**Installs an [InputChannel] using the given [ChannelHandle]. You can then subscribe to the
         * channel to start listening through it.*/
        public fun install(handle: ChannelHandle): InputChannel {
            return InputChannel(handle)
        }

        /**Installs an [InputChannel] using the given [MessageData]. You can then subscribe to the
         * channel to start listening through it.*/
        public fun install(handshakeMessage: MessageData): InputChannel = InputChannel(
            Message.decodePointer(handshakeMessage)
                ?: throw Exception("To install a channel using a handshake MessageData, the content pointer must point to a valid ChannelData struct.")
        )

    }
}

/**The receiving end of a [Channel], used to respond to events sent through it. Create instances of this class by
 * calling [Channel.install].*/
public class InputChannel internal constructor(handle: ChannelHandle) : Channel(handle) {
    private val stableRef = StableRef.create(this)

    init {
        handle.pointed.handler = ChannelCallbackHandler
        handle.pointed.kotlinRef = stableRef.asCPointer()
    }

    internal var listener: MessageListener? = null

    /**Remove the listener added through [listen] or [subscribe]. A new listener can still be registered. To definitely
     * close the receiving end of the channel, use the [disconnect] method.*/
    public fun unsubscribe() {
        listener = null
    }

    /**Listen for messages sent over this channel. The [MessageData] returned from the [response] is sent back to the
     * owner of this channel.
     *
     * If you don't intend to respond to any messages, use [subscribe] instead.
     *
     * Bear in mind that only one [MessageListener] may be active at the same time, so repeated calls to
     * this method or [listen] will override the previous listener.*/
    public fun listen(response: (MessageData) -> MessageData?)  {
        listener = response
    }

    /**Subscribe to messages sent over this channel. Use this method instead of [listen] if you don't intend to issue
     * a response.
     *
     * Bear in mind that only one [MessageListener] may be active at the same time, so repeated calls to
     * this method or [listen] will override the previous listener.*/
    public inline fun subscribe(crossinline callback: (MessageData) -> Unit): Unit = listen {
        callback(it)
        null
    }

    /**Subscribe to messages sent over this channel. Messages are decoded by the [interpreter] before being passed on
     * to the [callback]. Use this method instead of [listen] if you don't intend to issue
     * a response.
     *
     * Bear in mind that only one [MessageListener] may be active at the same time, so repeated calls to
     * this method or [listen] will override the previous listener.*/
    public inline fun <T : Message<*>> subscribe(
        interpreter: MessageInterpreter<T>,
        crossinline callback: (T) -> Unit
    ): Unit = listen {
        callback(interpreter.decode(it))
        null
    }

    /**Listen for messages sent over this channel. Messages are decoded by the [interpreter] before being passed on
     * to the [response] callback. The [MessageData] returned from [response] is sent back to the owner of
     * this channel.
     *
     * If you don't intend to respond to any messages, use [subscribe] instead.
     *
     * Bear in mind that only one [MessageListener] may be active at the same time, so repeated calls to
     * this method or [listen] will override the previous listener.*/
    public inline fun <T : Message<*>> listen(
        interpreter: MessageInterpreter<T>,
        crossinline response: (T) -> MessageData?
    ): Unit = listen {
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

/**The emitting end of a [Channel], used to send [MessageData] structs to a listener. Create instances of this class
 * by calling [Channel.open]. Send the [handshake] message to a dynamically loaded module to establish a communication
 * channel.*/
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

    /**Send a [message] through the channel, note that the message might not be processed if the channel has not
     * been installed yet (see [open]).
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