package io.github.mariuszmarzec.kompiler

import io.github.mariuszmarzec.onp.onpKompiler
import org.junit.Test
import kotlin.test.assertEquals

class KompilerTest {

    val onpKompiler = onpKompiler()

    @Test
    fun onpTest() {
        assertEquals("first second +", onpKompiler.compile("first + second"))
        assertEquals("2 3 + 5 *", onpKompiler.compile("(2+3)*5"))
        assertEquals("2 3 plus 5 times", onpKompiler.compile("(2 plus 3) times 5"))
        assertEquals("2 7 + 3 / 14 3 - 4 * + 2 /", onpKompiler.compile("((2+7)/3+(14-3)*4)/2"))
        assertEquals("12 a b c * d e / + * +", onpKompiler.compile("12 + a * (b * c + d / e)"))
        assertEquals("1 two plus", onpKompiler.compile("1 plus two"))
    }
}
