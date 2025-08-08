package io.github.mariuszmarzec.kompiler

import org.junit.Test
import kotlin.test.assertEquals

class KompilerTest {

    val kompiler = Kompiler()

    @Test
    fun onpTest() {
        assertEquals("first second +", kompiler.compile("first + second"))
        assertEquals("2 3 + 5 *", kompiler.compile("(2+3)*5"))
        assertEquals("2 7 + 3 / 14 3 − 4 * + 2 /", kompiler.compile("((2+7)/3+(14−3)*4)/2"))
        assertEquals("12 a b c * d e / + * +", kompiler.compile("12 + a * (b * c + d / e)"))
    }
}