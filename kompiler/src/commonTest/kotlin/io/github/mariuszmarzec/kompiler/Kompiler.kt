package io.github.mariuszmarzec.kompiler

import io.github.mariuszmarzec.onp.onpKompiler
import org.junit.Test
import kotlin.test.assertEquals

class KompilerTest {

    val onpKompiler = onpKompiler()

    @Test
    fun onpTest() {
        assertEquals("first second +", call("first + second"))
        assertEquals("2 3 + 5 *", call("(2+3)*5"))
        assertEquals("2 3 plus 5 times", call("(2 plus 3) times 5"))
        assertEquals("2 7 + 3 / 14 3 - 4 * + 2 /", call("((2+7)/3+(14-3)*4)/2"))
        assertEquals("12 a b c * d e / + * +", call("12 + a * (b * c + d / e)"))
        assertEquals("1 two plus", call("1 plus two"))

        // function call
        val astOnp = onpKompiler.compile("1 plus pow(2, 2)").value
        assertEquals("1 2 2 , pow plus", astOnp.intermediate())
        assertEquals("5", astOnp.run())
//        assertEquals("1 a b + 2 , pow -", call("1 - pow(a + b, c)"))
    }

    private fun call(exp: String): String = onpKompiler.compile(exp).value.intermediate()
}
