package io.github.mariuszmarzec.kompiler

import io.github.mariuszmarzec.onp.onpKompiler
import org.junit.Test
import kotlin.test.assertEquals

class KompilerTest {

    val onpKompiler = onpKompiler()

    @Test
    fun onpTest() {
        assertEquals("first second + EOL", call("first + second"))
        assertEquals("2 3 + 5 * EOL", call("(2+3)*5"))
        assertEquals("2 3 plus 5 times EOL", call("(2 plus 3) times 5"))
        assertEquals("2 7 + 3 / 14 3 - 4 * + 2 / EOL", call("((2+7)/3+(14-3)*4)/2"))
        assertEquals("12 a b c * d e / + * + EOL", call("12 + a * (b * c + d / e)"))
        assertEquals("1 two plus EOL", call("1 plus two"))
    }

    @Test
    fun onpFunction1CallTest() {
        val astOnp = onpKompiler.compile("1 plus pow2(2)").value
        assertEquals("1 2 pow2 plus EOL", astOnp.intermediate())
        assertEquals("5", astOnp.run())
    }

    @Test
    fun onpFunction1CallOverloadTest() {
        val astOnp = onpKompiler.compile("1 plus pow(2)").value
        assertEquals("1 2 pow plus EOL", astOnp.intermediate())
        assertEquals("5", astOnp.run())
    }

    @Test
    fun onpFunction2CallTest() {
        // function call
        val astOnp = onpKompiler.compile("1 plus pow(2, 2)").value
        assertEquals("5", astOnp.run())
        assertEquals("1 2 2 , pow plus EOL", astOnp.intermediate())
        assertEquals("1 a b + c , pow - EOL", call("1 - pow(a + (b), c)"))
        assertEquals("1 a b + c , pow - EOL", call("1 - pow(a + b, c)"))
        assertEquals("1 a b + d , pow c , pow - EOL", call("1 - pow(pow(a + b, d), c)"))
    }

    @Test
    fun onpFunction3CallTest() {
        // function call
        val astOnp = onpKompiler.compile("1 plus min3(3, 1, 2)").value
        assertEquals("2", astOnp.run())
        assertEquals("1 3 1 , 2 , min3 plus EOL", astOnp.intermediate())
    }

    @Test
    fun assignmentOperator() {
        val astOnp = onpKompiler.compile("val a is 1 plus 2").value
        assertEquals("a val 1 2 plus is EOL", astOnp.intermediate())
    }

    @Test
    fun endOfLine() {
        val astOnp = onpKompiler.compile(
            """
                val a = 1
                val b = a + 1
                pow(2)
            """.trimIndent()
        ).value
        assertEquals("4", astOnp.run())
    }

    @Test
    fun functionDeclaration() {
        val astOnp = onpKompiler.compile(
            """
                fun addOne(x) { x plus 1; }
                addOne(0)
            """.trimIndent()
        ).value
        assertEquals("1", astOnp.run())
    }

    @Test
    fun functionDeclarationOverload() {
        val astOnp = onpKompiler.compile(
            """
                fun add(a) { a plus 1; }
                fun add(a, b) { a plus b; }
                add(add(0, 1))
            """.trimIndent()
        ).value
        assertEquals("2", astOnp.run())
    }

    @Test
    fun functionOverloadingDeclaration() {
        val astOnp = onpKompiler.compile(
            """
                fun add(a) { a plus 1; }
                fun add(a, b) { a plus pow(b) - add(0); }
                add(add(0, 1))
            """.trimIndent()
        ).value
        assertEquals("1", astOnp.run())
    }

    private fun call(exp: String): String = onpKompiler.compile(exp).value.intermediate()
}
