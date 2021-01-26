@file:Suppress("unused")

package modular.kotlin

import kotlinx.cinterop.CFunction
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.invoke
import modular.kotlin.channels.*
import modular.kotlin.interop.MessageData
import modular.kotlin.interop.loadLibrary

public typealias HandshakeProcedure = CPointer<CFunction<(ChannelHandle) -> Boolean>>

public open class ModuleHost protected constructor(
    public val name: String,
    private val handle: NativeHandle,
    private val channel: OutputChannel,
) {
    public companion object {
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

        public inline fun load(path: String): ModuleHost {
            return load(path, ::ModuleHost)
        }
    }

    public fun sendMessage(message: MessageData): MessageData? {
        return channel.send(message)
    }

    public fun <M : Message<*>> sendMessage(message: M): MessageData? {
        return channel.send(message)
    }

    public open fun unload() {
        channel.send(MessageData(-1))
        handle.unload()
    }
}