import modular.kotlin.channels.CompositeMessage
import modular.kotlin.channels.Message
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

const val GREETING = "Hello"
const val AGE = 45

@Test
fun compositingTest() {
    val oldBlob = Random.nextBytes(8)
    val data = Message.compose {
        set("greeting", GREETING)
        set("age", AGE)
        set("blob", oldBlob)
    }

    val message = CompositeMessage.decode(data)

    val blob: ByteArray by message
    assert(blob.contentEquals(oldBlob))

    val greeting: String by message
    assertEquals(greeting, GREETING)

    val age: Int? = message.extract("age")
    assertNotNull(age)
    assertEquals(age, AGE)
}