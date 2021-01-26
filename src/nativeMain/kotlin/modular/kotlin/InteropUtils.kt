package modular.kotlin

import kotlinx.cinterop.CFunction
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.toCPointer
import modular.kotlin.interop.loadFunction
import modular.kotlin.interop.unloadLibrary
import platform.posix.intptr_t

public typealias NativeHandle = intptr_t

internal fun <C : Function<*>> NativeHandle.getFunction(name: String): CPointer<CFunction<C>>? {
    return loadFunction(this, name).toCPointer()
}

internal fun NativeHandle.unload(): Int {
    return unloadLibrary(this)
}