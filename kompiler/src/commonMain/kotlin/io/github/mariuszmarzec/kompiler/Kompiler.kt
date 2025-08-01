package io.github.mariuszmarzec.kompiler

class Kompiler {

    fun compile(exp: String): String {
        val output = mutableListOf<String>()
        val stack = ArrayDeque<String>()
        for (ch in exp) {
            printInput(output)
            println("ch = $ch")
            when (ch) {
                in allowedCharacters() -> {
                    println("allowedCharacters")
                    if (output.lastOrNull()?.first() in allowedCharacters()) {
                        output[output.lastIndex] += ch
                    } else {
                        output.add(ch.toString())
                    }
                }
                in openingOperator() -> {
                    stack.addLast(ch.toString())
                }
                in closingOperator() -> {
                    while (stack.isNotEmpty() && stack.last() != "(") {
                        output.add(stack.removeLast())
                    }
                    if (stack.isNotEmpty() && stack.last() == "(") {
                        stack.removeLast() // Remove the '('
                    }
                    else {
                        throw IllegalArgumentException("Mismatched parentheses in expression: $exp")
                    }
                }
                ' ' -> {
                    // Ignore whitespace
                }
                in regularOperators() -> {
                    while (stack.isNotEmpty() && operators()[ch]!! <= operators()[stack.last().first()]!!) {
                        output.add(stack.removeLast())
                    }
                    stack.addLast(ch.toString())
                }
                else -> {
                    throw IllegalArgumentException("Invalid character in expression: $ch")
                }
            }
            println(printInput(output))
        }
        while (stack.isNotEmpty()) {
            output.add(stack.removeLast())
        }
        return printInput(output)
    }

    private fun closingOperator() = listOf(')')

    private fun openingOperator(): List<Char> = listOf('(')

    private fun allowedCharacters(): List<Char> =
        (('0'..'9') + ('a'..'z') + ('A'..'Z'))

    private fun operators(): Map<Char, Int> = mapOf(
        '(' to 0,
        '-' to 0,
        '+' to 1,
        ')' to 1,
        '*' to 2,
        '/' to 2,
        '%' to 2,
        '^' to 3
    )

    private fun regularOperators(): List<Char> =
        operators().keys.filter { it !in openingOperator() && it !in closingOperator() }
}

private fun printInput(input: MutableList<String>): String = input.fold(StringBuilder()) { acc, item ->
    if (!acc.isEmpty()) {
        acc.append(" ")
    }
    acc.append(item)
}.toString()