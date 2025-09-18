package io.github.mariuszmarzec.kompiler

class Kompiler(val readers: List<TokenReader>) {

    fun compile(exp: String): String {
        val output = mutableListOf<Token>()
        val stack = ArrayDeque<Token>()
        exp.forEachIndexed { index, ch ->
            readers.firstOrNull { it.allowedCharacters.contains(ch) }
                ?.handleCharacter(exp, index, ch, output, stack)
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

    fun handleCharacter(
        exp: String,
        index: Int,
        ch: Char,
        output: MutableList<Token>,
        stack: ArrayDeque<Token>
    )
}

interface TokenHandler {

    val tokenType: String

    fun handleToken(token: Token, stack: ArrayDeque<Token>, output: MutableList<Token>, exp: String)
}

class OperatorTokenHandler : TokenHandler {

    override val tokenType: String = "operator"

    override fun handleToken(
        token: Token,
        stack: ArrayDeque<Token>,
        output: MutableList<Token>,
        exp: String
    ) {
        when (token.value) {
            in openingOperator() -> {
                stack.addLast(token)
            }

            in closingOperator() -> {
                while (stack.isNotEmpty() && stack.last().value != "(") {
                    output.add(stack.removeLast())
                }
                if (stack.isNotEmpty() && stack.last().value == "(") {
                    stack.removeLast() // Remove the '('
                } else {
                    throw IllegalArgumentException("Mismatched parentheses in expression: $exp")
                }
            }

            in regularOperators() -> {
                while (stack.isNotEmpty() && operators()[token.value]!! <= operators()[stack.last().value]!!) {
                    val element = stack.removeLast()
                    output.add(element)
                }
                stack.addLast(token)
            }

            else -> {
                throw IllegalArgumentException("Lacking handling for Token: ${token.value} at index ${token.index} in expression: $exp")
            }
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

    override fun handleCharacter(
        exp: String,
        index: Int,
        ch: Char,
        output: MutableList<Token>,
        stack: ArrayDeque<Token>
    ) {
        closeLastToken(output)

        val tokenOperator = Token(index, ch.toString(), opened = false, type = "operator")
        tokenHandler.handleToken(tokenOperator, stack, output, exp)
    }
}

class LiteralReader : TokenReader {
    override val allowedCharacters: Set<Char>
        get() = (('0'..'9') + ('a'..'z') + ('A'..'Z')).toSet()

    override fun handleCharacter(
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

class WhiteSpaceReader : TokenReader {
    override val allowedCharacters: Set<Char>
        get() = setOf(' ')

    override fun handleCharacter(
        exp: String,
        index: Int,
        ch: Char,
        output: MutableList<Token>,
        stack: ArrayDeque<Token>
    ) {
        closeLastToken(output)
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

class CompositeTokenHandler(handlers: List<TokenHandler>) : TokenHandler {

    private val handlers: Map<String, TokenHandler> = handlers.associateBy { it.tokenType }

    override val tokenType: String
        get() = throw IllegalCallerException("CompositeTokenHandler does not have a single tokenType")

    override fun handleToken(
        token: Token,
        stack: ArrayDeque<Token>,
        output: MutableList<Token>,
        exp: String
    ) {
        handlers[token.type]
            ?.handleToken(token, stack, output, exp)
            ?: throw IllegalArgumentException("Lacking handling for Token: ${token.value} at index ${token.index} in expression: $exp")
    }
}