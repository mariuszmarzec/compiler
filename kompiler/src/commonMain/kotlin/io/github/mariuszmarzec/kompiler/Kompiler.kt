package io.github.mariuszmarzec.kompiler

class Kompiler {

    fun compile(exp: String): String {
        val operatorHandler = OperatorHandler()
        val literalHandler = LiteralHandler()

        val output = mutableListOf<Token>()
        val stack = ArrayDeque<Token>()
        exp.forEachIndexed { index, ch ->
            printInput(output)
            println("ch = $ch")
            when (ch) {
                in literalHandler.allowedCharacters -> {
                    literalHandler.handleToken(exp, index, ch, output, stack)
                }
                in operatorHandler.allowedCharacters -> {
                    operatorHandler.handleToken(exp, index, ch, output, stack)
                }
                ' ' -> {
                    // TODO handle whitespace as allowed after last one or not allowed
                }
                else -> {
                    throw IllegalArgumentException("Invalid character: `$ch` at index $index in expression: $exp")
                }
            }
            println(printInput(output))
        }
        while (stack.isNotEmpty()) {
            output.add(stack.removeLast())
        }
        return printInput(output)
    }

    private fun allowedCharacters(): List<String> =
        (('0'..'9') + ('a'..'z') + ('A'..'Z')).map { it.toString() }

}

private fun printInput(input: MutableList<Token>): String = input.fold(StringBuilder()) { acc, item ->
    if (!acc.isEmpty()) {
        acc.append(" ")
    }
    acc.append(item.value)
}.toString()

interface Token {

    val index: Int

    val value: String

    val opened: Boolean
}

interface TokenHandler {

    val allowedCharacters: List<Char>

    fun handleToken(
        exp: String,
        index: Int,
        ch: Char,
        output: MutableList<Token>,
        stack: ArrayDeque<Token>
    )
}

class OperatorHandler : TokenHandler {
    override val allowedCharacters: List<Char>
        get() = listOf('(', ')', '+', '-', '*', '/', '%', '^')

    override fun handleToken(
        exp: String,
        index: Int,
        ch: Char,
        output: MutableList<Token>,
        stack: ArrayDeque<Token>
    ) {
        when(ch.toString()) {
            in openingOperator() -> {
                stack.addLast(Operator(index, ch.toString(), opened = false))
            }
            in closingOperator() -> {
                while (stack.isNotEmpty() && stack.last().value != "(") {
                    output.add(stack.removeLast())
                }
                if (stack.isNotEmpty() && stack.last().value == "(") {
                    stack.removeLast() // Remove the '('
                }
                else {
                    throw IllegalArgumentException("Mismatched parentheses in expression: $exp")
                }
            }
            in regularOperators() -> {
                while (stack.isNotEmpty() && operators()["$ch"]!! <= operators()[stack.last().value]!!) {
                    output.add(stack.removeLast())
                }
                stack.addLast(Operator(index, ch.toString(), opened = false))
            }
            else -> {
                throw IllegalArgumentException("Lacking handling for operator: $ch at index $index in expression: $exp")
            }
        }
    }

    private fun closingOperator() = listOf(")")

    private fun openingOperator() = listOf("(")

    fun operators(): Map<String, Int> = mapOf(
        "(" to 0,
        "-" to 0,
        "+" to 1,
        ")" to 1,
        "*" to 2,
        "/" to 2,
        "%" to 2,
        "^" to 3
    )

    private fun regularOperators(): List<String> =
        operators().keys.filter { it !in openingOperator() && it !in closingOperator() }
}

class LiteralHandler : TokenHandler {
    override val allowedCharacters: List<Char>
        get() = ('0'..'9') + ('a'..'z') + ('A'..'Z')

    override fun handleToken(
        exp: String,
        index: Int,
        ch: Char,
        output: MutableList<Token>,
        stack: ArrayDeque<Token>
    ) {
        println("allowedCharacters")
        val last = output.lastOrNull()
        if (last is Literal) {
            output[output.lastIndex] = last.copy(value = last.value + ch)
        } else {
            output.add(Literal(index, ch.toString(), opened = false))
        }
    }
}

data class Operator(override val index: Int, override val value: String, override val opened: Boolean) : Token

data class Literal(override val index: Int, override val value: String, override val opened: Boolean) : Token
