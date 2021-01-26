package modular.kotlin

import kotlinx.cinterop.CFunction
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.toCPointer
import modular.kotlin.interop.loadFunction
import modular.kotlin.interop.unloadLibrary
import platform.posix.intptr_t

/**A platform-agnostic representation of a pointer to a loaded dynamic library.
 *
 * On Windows, this is obtained through the [LoadLibraryEx][platform.windows.LoadLibraryEx] function. On Linux
 * and MacOS, regular POSIX functions are used.*/
public typealias NativeHandle = intptr_t

/**Loads the function identified by [name] from this library. The generic parameter [F] indicates the Kotlin
 *  functional type the [CFunction] should be wrapping.
 *
 *  @return a pointer to the specified function, or null if it is not found.*/
public inline fun <F : Function<*>> NativeHandle.getFunction(name: String): CPointer<CFunction<F>>? {
    return loadFunction(this, name).toCPointer()
}

/**Unload this library from memory.
 *
 * Be careful when calling this, if a [Channel][modular.kotlin.channels.Channel] is still open at this time, attempts
 * to send a message through it might result in obscure crashes due to message handlers being unloaded from memory.*/
public inline fun NativeHandle.unload(): Int {
    return unloadLibrary(this)
}