@file:Suppress("unused")

package modular.kotlin

import kotlinx.cinterop.CFunction
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.invoke
import modular.kotlin.channels.*
import modular.kotlin.interop.MessageData
import modular.kotlin.interop.loadLibrary

/**Shorthand for writing [CPointer]<[CFunction]<([ChannelHandle]) -> [Boolean]>>, which is the signature of the
 * handshake function used to load modules.*/
public typealias HandshakeProcedure = CPointer<CFunction<(ChannelHandle) -> Boolean>>

/**Base class used to load Kotlin modules in runtime.
 *
 * Note that this class is meant to be used by *client* code, in order to actually implement a custom module you
 * must use the [Module] class and compile your project to a dynamic library.
 *
 * For a description of the lifecycle of modules and hosts, see [Module].
 *
 * You cannot create instances of this type directly, instead you should use the [load] function, which automatically
 * loads a dynamic library from the a given path, and establishes a communication [Channel] with the module.
 *
 * Once the module has been loaded, use [sendMessage] to communicate with it, if the module is properly implemented,
 * it will process the messages in its [Module.handleMessage] method.
 *
 * Don't forget to call [unload], this will send the proper signal to the module, and then unload the dynamic library
 * from memory.
 * */
public open class ModuleHost protected constructor(
    /**A proper name for the hosted module, usually the name of the file it was loaded from.*/
    public val name: String,
    /**A handle to the native shared library holding the module's implementation.*/
    private val handle: NativeHandle,
    /**A [Channel] used to communicate with the module.*/
    private val channel: OutputChannel,
) {
    public companion object {
        /**Locates a dynamic library at [path] and loads it as a Kotlin [Module]. The library should expose a
         * handshake function as described in [HandshakeProcedure]. The [factory] method receives a generated name
         * for the module, the [NativeHandle] to the dynamic library and the [OutputChannel] used to communicate with
         * it. Do not use these directly, they should only be used as constructor parameters for [ModuleHost]
         * subclasses.*/
        public fun <T : ModuleHost> load(path: String, factory: (String, NativeHandle, OutputChannel) -> T): T {
            // Load the native module
            val nativeHandle =
                loadLibrary(path).takeUnless { it == 0L } ?: throw ModuleLoadException("Failed to load module at $path")

            // Locate the handshake procedure in the native module
            val handshakeProc = nativeHandle.getFunction<(ChannelHandle) -> Boolean>("handshake")
                ?: throw ModuleLoadException("Failed to find handshake function in module $path")

            // Open a channel to communicate with the module
            val channel = Channel.open()

            // Send handshake and verify the response
            if (!handshakeProc(channel.handle))
                throw ModuleLoadException("Handshake failed for module at $path")

            // Create the host handler and return
            return factory(
                path.substringAfterLast('\\').substringBeforeLast("."),
                nativeHandle,
                channel
            )
        }

        /**Simplified version of the method, used to load a module with a basic [ModuleHost]. You can then call
         *  [sendMessage] to communicate with the module.*/
        public inline fun load(path: String): ModuleHost {
            return load(path, ::ModuleHost)
        }
    }

    /**Send a message to the hosted module. The message will be processed by the module's [Module.handleMessage]
     *  method.*/
    public fun sendMessage(message: MessageData): MessageData? {
        return channel.send(message)
    }

    public fun <M : Message<*>> sendMessage(message: M): MessageData? {
        return channel.send(message)
    }

    /**Unloads the hosted module, this method makes this [ModuleHost] invalid as well. Use [load] to create another
     * [ModuleHost] instance to communicate with a different module.*/
    public open fun unload() {
        channel.send(MessageData(-1))
        handle.unload()
    }
}