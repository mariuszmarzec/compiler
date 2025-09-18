package io.github.mariuszmarzec.kompiler

class Kompiler(val readers: List<TokenReader>) {

    fun compile(exp: String): String {
        val output = mutableListOf<Token>()
        val stack = ArrayDeque<Token>()
        exp.forEachIndexed { index, ch ->
            readers.firstOrNull { it.allowedCharacters.contains(ch) }
                ?.readChar(exp, index, ch, output, stack)
                ?: throw IllegalArgumentException("Unexpected character '$ch' at index $index in expression: $exp")
        }
        while (stack.isNotEmpty()) {
            output.add(stack.removeLast())
        }
        return printInput(output)
    }
}

private fun closeLastToken(output: MutableList<Token>) {
    output.lastOrNull()?.let { last ->
        output[output.lastIndex] = last.copy(opened = false)
    }
}

private fun printInput(input: MutableList<Token>): String = input.fold(StringBuilder()) { acc, item ->
    if (!acc.isEmpty()) {
        acc.append(" ")
    }
    acc.append(item.value)
}.toString()

data class Token(
    val index: Int,
    val value: String,
    val opened: Boolean,
    val type: String
)

interface TokenReader {

    val allowedCharacters: Set<Char>

    fun readChar(
        exp: String,
        index: Int,
        ch: Char,
        output: MutableList<Token>,
        stack: ArrayDeque<Token>
    )
}

interface TokenHandler {

    fun handleToken(token: Token, stack: ArrayDeque<Token>, output: MutableList<Token>, exp: String): Boolean
}

class OperatorTokenHandler : TokenHandler {

    override fun handleToken(
        token: Token,
        stack: ArrayDeque<Token>,
        output: MutableList<Token>,
        exp: String
    ): Boolean {
        return when (token.value) {
            in openingOperator() -> {
                removeOperatorFromOutputLast(output, token)
                stack.addLast(token)
                true
            }

            in closingOperator() -> {
                removeOperatorFromOutputLast(output, token)
                while (stack.isNotEmpty() && stack.last().value != "(") {
                    output.add(stack.removeLast())
                }
                if (stack.isNotEmpty() && stack.last().value == "(") {
                    stack.removeLast() // Remove the '('
                    true
                } else {
                    throw IllegalArgumentException("Mismatched parentheses in expression: $exp")
                }
            }

            in regularOperators() -> {
                removeOperatorFromOutputLast(output, token)
                while (stack.isNotEmpty() && operators()[token.value]!! <= operators()[stack.last().value]!!) {
                    val element = stack.removeLast()
                    output.add(element)
                }
                stack.addLast(token)
                true
            }

            else -> {
                false
            }
        }
    }

    /**
     * workaround added because in current implmentation we don't have currentReadToken variable. Instead of we have
     * output stack, operator should be store them
     */
    private fun removeOperatorFromOutputLast(
        output: MutableList<Token>,
        token: Token
    ) {
        if (output.lastOrNull() == token) {
            output.removeLast()
        }
    }

    private fun closingOperator() = listOf(")")

    private fun openingOperator() = listOf("(")

    private fun regularOperators(): List<String> =
        operators().keys.filter { it !in openingOperator() + closingOperator() }
}

class OperatorReader(private val tokenHandler: TokenHandler) : TokenReader {

    override val allowedCharacters: Set<Char>
        get() = operators().keys.flatMap { it.split("").mapNotNull { it.firstOrNull() } }.toSet()

    override fun readChar(
        exp: String,
        index: Int,
        ch: Char,
        output: MutableList<Token>,
        stack: ArrayDeque<Token>
    ) {
        closeLastToken(output)

        val tokenOperator = Token(index, ch.toString(), opened = false, type = "operator")
        if (!tokenHandler.handleToken(tokenOperator, stack, output, exp)) {
            throw IllegalArgumentException("Lacking handling for Token: `${tokenOperator.value}` at index ${tokenOperator.index} in expression: $exp")
        }
    }
}

class LiteralReader() : TokenReader {

    override val allowedCharacters: Set<Char>
        get() = (('0'..'9') + ('a'..'z') + ('A'..'Z')).toSet()

    override fun readChar(
        exp: String,
        index: Int,
        ch: Char,
        output: MutableList<Token>,
        stack: ArrayDeque<Token>
    ) {
        val last = output.lastOrNull()
        if (last != null && last.opened) {
            output[output.lastIndex] = last.copy(value = last.value + ch)
        } else {
            output.add(Token(index, ch.toString(), opened = true, type = "literal"))
        }
    }
}

class WhiteSpaceReader(private val tokenHandler: TokenHandler) : TokenReader {

    override val allowedCharacters: Set<Char>
        get() = setOf(' ')

    override fun readChar(
        exp: String,
        index: Int,
        ch: Char,
        output: MutableList<Token>,
        stack: ArrayDeque<Token>
    ) {
        if (output.lastOrNull()?.opened == true) {
        closeLastToken(output)

        tokenHandler.handleToken(output.last(), stack, output, exp)
}
    }
}

fun operators(): Map<String, Int> = mapOf(
    "(" to -1,
    "−" to 0,
    "+" to 1,
    "plus" to 1,
    ")" to 1,
    "*" to 2,
    "/" to 2,
    "%" to 2,
    "^" to 3
)

class CompositeTokenHandler(private val handlers: List<TokenHandler>) : TokenHandler {

    override fun handleToken(
        token: Token,
        stack: ArrayDeque<Token>,
        output: MutableList<Token>,
        exp: String
    ): Boolean {
        return handlers
            .firstOrNull<TokenHandler> { it.handleToken(token, stack, output, exp) }
            .let<TokenHandler?, Boolean> { it != null }
    }
}