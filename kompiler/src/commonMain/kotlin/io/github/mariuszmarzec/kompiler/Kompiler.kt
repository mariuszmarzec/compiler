package io.github.mariuszmarzec.kompiler

class Kompiler(val handlers: List<TokenHandler>) {

    fun compile(exp: String): String {
        val output = mutableListOf<Token>()
        val stack = ArrayDeque<Token>()
        val currentToken
        exp.forEachIndexed { index, ch ->
            handlers.firstOrNull { it.allowedCharacters.contains(ch) }
                ?.handleToken(exp, index, ch, output, stack)
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

interface TokenHandler {

    val allowedCharacters: Set<Char>

    fun handleToken(
        exp: String,
        index: Int,
        ch: Char,
        output: MutableList<Token>,
        stack: ArrayDeque<Token>
    )
}

class OperatorHandler : TokenHandler {
    override val allowedCharacters: Set<Char>
        get() = operators().keys.flatMap { it.split("").mapNotNull { it.firstOrNull() } }.toSet()

    override fun handleToken(
        exp: String,
        index: Int,
        ch: Char,
        output: MutableList<Token>,
        stack: ArrayDeque<Token>
    ) {
        closeLastToken(output)

        val tokenOperator = Token(index, ch.toString(), opened = false, type = "operator")
        handleOperator(tokenOperator, stack, output, exp)
    }

    private fun handleOperator(
        tokenOperator: Token,
        stack: ArrayDeque<Token>,
        output: MutableList<Token>,
        exp: String
    ) {
        when (tokenOperator.value) {
            in openingOperator() -> {
                stack.addLast(tokenOperator)
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
                while (stack.isNotEmpty() && operators()[tokenOperator.value]!! <= operators()[stack.last().value]!!) {
                    val element = stack.removeLast()
                    output.add(element)
                }
                stack.addLast(tokenOperator)
            }

            else -> {
                throw IllegalArgumentException("Lacking handling for Token: ${tokenOperator.value} at index ${tokenOperator.index} in expression: $exp")
            }
        }
    }

    private fun closingOperator() = listOf(")")

    private fun openingOperator() = listOf("(")

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

    private fun regularOperators(): List<String> =
        operators().keys.filter { it !in openingOperator() + closingOperator() }
}

class LiteralHandler : TokenHandler {
    override val allowedCharacters: Set<Char>
        get() = (('0'..'9') + ('a'..'z') + ('A'..'Z')).toSet()

    override fun handleToken(
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

class WhiteSpaceHandler : TokenHandler {
    override val allowedCharacters: Set<Char>
        get() = setOf(' ')

    override fun handleToken(
        exp: String,
        index: Int,
        ch: Char,
        output: MutableList<Token>,
        stack: ArrayDeque<Token>
    ) {
        closeLastToken(output)
    }
}
