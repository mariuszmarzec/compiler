package io.github.mariuszmarzec.kompiler

import org.junit.Test
import kotlin.test.assertEquals

class KompilerTest {

    val handlers = listOf(LiteralHandler(), OperatorHandler(), WhiteSpaceHandler())
    val onpKompiler = Kompiler(handlers)

    @Test
    fun onpTest() {
        assertEquals("first second +", onpKompiler.compile("first + second"))
        assertEquals("2 3 + 5 *", onpKompiler.compile("(2+3)*5"))
        assertEquals("2 7 + 3 / 14 3 − 4 * + 2 /", onpKompiler.compile("((2+7)/3+(14−3)*4)/2"))
        assertEquals("12 a b c * d e / + * +", onpKompiler.compile("12 + a * (b * c + d / e)"))
    }
}