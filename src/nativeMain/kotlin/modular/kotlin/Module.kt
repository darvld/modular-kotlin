@file:Suppress("unused","MemberVisibilityCanBePrivate")

package modular.kotlin

import modular.kotlin.channels.Channel
import modular.kotlin.channels.ChannelHandle
import modular.kotlin.channels.InputChannel
import modular.kotlin.interop.MessageData

/**Abstract base class that represents a Kotlin shared library loaded in runtime.
 *
 * Note that this class is used to simplify the *development* of modules, not *using* them. If you want to load a
 * module in runtime and communicate with it, use the [ModuleHost] class.
 *
 * In general, the lifetime of a Kotlin [Module] goes as follows:
 *  1. The module is loaded using [ModuleHost.load]. To establish the connection, the host first looks for a static C
 *  function named "handshake", which must be present as a top-level function declaration with the @[CName] annotation.
 *  The handshake function takes a [ChannelHandle] as single parameter and returns a [Boolean] indicating the success
 *  of the loading operation. At this point you should call the [load] implementation of your custom [Module] subclass
 *  to allow its initialization.
 *  2. The module then starts listening for messages provided through the [eventsChannel] (the connection with the host).
 *  All important events will be relayed through it, thus you should pair custom [Module] subclasses with custom
 *  [ModuleHost] subclasses to provide a more concise integration. Messages sent by the host are processed by the
 *  [handleMessage] function.
 *  3. The module is unloaded, either by manually calling [unload] (not recommended), or when the "unload" message is
 *  received from the host. This message is handled automatically by the [Module] base class, so in order to react to
 *  it you should override the [unload] method (don't forget to call the super implementation).
 *  */
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

    /**Shortcut for handling messages without explicitly returning null. This method runs [block] and returns null
     * automatically.*/
    protected inline fun handle(message: MessageData, block: (MessageData) -> Unit): MessageData? {
        block(message)
        return null
    }

    /**Callback method invoked when a plugin is loaded. You must call this from the handshake function.*/
    protected abstract fun load(): Boolean

    /**Disconnect this module's event channel and effectively terminate it. When overriding this function you must
     * call the base implementation.*/
    protected open fun unload() {
        eventsChannel.disconnect()
    }

    /**Handle a message sent by the host through the events channel. Use this to process and respond to host events.*/
    protected abstract fun handleMessage(message: MessageData): MessageData?

    public companion object {
        /**Install a [Module] subclass using the provided channel [handle]. This is a shortcut method used in the
         * handshake procedure, equivalent to:
         * ```
         * @CName("handshake")
         * fun handshake(handle: ChannelHandle): Boolean {
         *     // Install the channel
         *     val channel = Channel.install(handle)
         *
         *     // Create the module instance
         *     val myModule = MyModuleSubclass(channel)
         *
         *     ...
         * }
         *
         * // Using the shortcut looks like:
         * @CName("handshake")
         * fun handshake(handle: ChannelHandle): Boolean {
         *     return Module.install(handle, ::MyModuleSubclass).load()
         * }
         * ```
         * */
        public inline fun <T : Module> install(handle: ChannelHandle, factory: (InputChannel) -> T): T {
            return factory(Channel.install(handle))
        }
    }
}