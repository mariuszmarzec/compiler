package io.github.mariuszmarzec.kompiler

data class AstOnp(
    val output: MutableList<Token> = mutableListOf<Token>(),
    val stack: ArrayDeque<Token> = ArrayDeque<Token>(),
    var currentReadToken: Token? = null,
): AstState<AstOnp> {

    override val value: AstOnp = this

    override fun update(action: AstOnp.() -> AstOnp) {
        this.action()
    }
}

interface AstState<T> {

    val value: T

    fun update(action: T.() -> T)
}

class Kompiler(private val readers: List<TokenReader<AstOnp>>, private val astBuilder: () -> AstState<AstOnp>) {

    fun compile(exp: String): String {
        var ast = astBuilder()
        exp.forEachIndexed { index, ch ->
            readers.firstOrNull { it.allowedCharacters.contains(ch) }
                ?.readChar(exp, index, ch, ast)
                ?: throw IllegalArgumentException("Unexpected character '$ch' at index $index in expression: $exp")
        }
        ast.update {
            while (stack.isNotEmpty()) {
                output.add(stack.removeLast())
            }
            this
        }
        return printInput(ast.value.output)
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

interface TokenReader<AST> {

    val allowedCharacters: Set<Char>

    fun readChar(
        exp: String,
        index: Int,
        ch: Char,
        ast: AstState<AST>
    )
}

interface TokenHandler<AST> {

    fun handleToken(token: Token, ast: AstState<AST>, exp: String): Boolean
}

class OperatorTokenHandler : TokenHandler<AstOnp> {

    override fun handleToken(
        token: Token,
        astState: AstState<AstOnp>,
        exp: String,
    ): Boolean {
        return when (token.value) {
            in openingOperator() -> {
                astState.update {
                    removeOperatorFromOutputLast(output, token)
                    stack.addLast(token)
                    this
                }
                true
            }

            in closingOperator() -> {
                var handled = false
                astState.update {
                    removeOperatorFromOutputLast(output, token)
                    while (stack.isNotEmpty() && stack.last().value != "(") {
                        output.add(stack.removeLast())
                    }
                    if (stack.isNotEmpty() && stack.last().value == "(") {
                        stack.removeLast() // Remove the '('
                        handled = true
                    }
                    this
                }
                handled || throw IllegalArgumentException("Mismatched parentheses in expression: $exp")
            }

            in regularOperators() -> {
                astState.update {
                    removeOperatorFromOutputLast(output, token)
                    while (stack.isNotEmpty() && operators()[token.value]!! <= operators()[stack.last().value]!!) {
                        val element = stack.removeLast()
                        output.add(element)
                    }
                    stack.addLast(token)
                    this
                }
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

class OperatorReader(private val tokenHandler: TokenHandler<AstOnp>) : TokenReader<AstOnp> {

    override val allowedCharacters: Set<Char>
        get() = operators().keys.flatMap { it.split("").mapNotNull { it.firstOrNull() } }.toSet()

    override fun readChar(
        exp: String,
        index: Int,
        ch: Char,
        astState: AstState<AstOnp>
    ) {
        astState.update {
            closeLastToken(output)
            this
        }

        val tokenOperator = Token(index, ch.toString(), opened = false, type = "operator")
        if (!tokenHandler.handleToken(tokenOperator, astState, exp)) {
            throw IllegalArgumentException("Lacking handling for Token: `${tokenOperator.value}` at index ${tokenOperator.index} in expression: $exp")
        }
    }
}

class LiteralReader() : TokenReader<AstOnp> {

    override val allowedCharacters: Set<Char>
        get() = (('0'..'9') + ('a'..'z') + ('A'..'Z')).toSet()

    override fun readChar(
        exp: String,
        index: Int,
        ch: Char,
        astState: AstState<AstOnp>
    ) {
        astState.update {
            val last = output.lastOrNull()
            if (last != null && last.opened) {
                output[output.lastIndex] = last.copy(value = last.value + ch)
            } else {
                output.add(Token(index, ch.toString(), opened = true, type = "literal"))
            }
            this
        }
    }
}

class WhiteSpaceReader(private val tokenHandler: TokenHandler<AstOnp>) : TokenReader<AstOnp> {

    override val allowedCharacters: Set<Char>
        get() = setOf(' ')

    override fun readChar(
        exp: String,
        index: Int,
        ch: Char,
        astState: AstState<AstOnp>
    ) {
        if (astState.value.output.lastOrNull()?.opened == true) {
            astState.update {
                closeLastToken(output)
                this
            }

            tokenHandler.handleToken(astState.value.output.last(), astState, exp)
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
    "times" to 2,
    "/" to 2,
    "%" to 2,
    "^" to 3
)

class CompositeTokenHandler(private val handlers: List<TokenHandler<AstOnp>>) : TokenHandler<AstOnp> {

    override fun handleToken(
        token: Token,
        astState: AstState<AstOnp>,
        exp: String
    ): Boolean {
        return handlers
            .firstOrNull<TokenHandler<AstOnp>> { it.handleToken(token, astState, exp) }
            .let<TokenHandler<AstOnp>?, Boolean> { it != null }
    }
}