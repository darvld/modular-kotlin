package modular.kotlin.channels

import modular.kotlin.interop.MessageData

/**Base interface for objects providing the ability to [decode] a [MessageData] struct, and transform it into a
 * specific [Message] subclass.
 *
 * This interface is usually implemented by the companion objects of [Message] subclasses (see [CompositeMessage] and
 * [BooleanMessage] for examples).*/
public fun interface MessageInterpreter<M : Message<*>> {
    /**Decode the [data] struct, returning a specific [Message] subclass, and usually freeing the associated memory.*/
    public fun decode(data: MessageData): M
}