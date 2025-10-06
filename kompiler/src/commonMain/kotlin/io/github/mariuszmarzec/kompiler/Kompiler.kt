package io.github.mariuszmarzec.kompiler

data class AstOnp(
    val output: MutableList<Token> = mutableListOf<Token>(),
    val stack: ArrayDeque<Token> = ArrayDeque<Token>(),
    var currentReadToken: Token? = null,
) : AstState<AstOnp> {

    override val value: AstOnp = this

    override fun update(action: AstOnp.() -> AstOnp) {
        this.action()
        printInput(output)
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
        // EOF READER
        ast.update {
            // clear any remaining read token
            currentReadToken?.let { readToken ->
                output.add(readToken)
                currentReadToken = null
            }

            while (stack.isNotEmpty()) {
                output.add(stack.removeLast())
            }
            this
        }
        return printInput(ast.value.output)
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

class OperatorsTokenHandler<T>(private val handlers: List<TokenHandler<T>>) : TokenHandler<T> {

    override fun handleToken(
        token: Token,
        astState: AstState<T>,
        exp: String,
    ): Boolean = handlers.firstOrNull { it.handleToken(token, astState, exp) }?.let { true } == true
}

val onpTokensHandlers: List<TokenHandler<AstOnp>> = listOf(
    OpeningParenthesisOnpTokenHandler(),
    ClosingParenthesisOnpTokenHandler(),
    RegularOperatorOnpTokenHandler(),
)

class OpeningParenthesisOnpTokenHandler : TokenHandler<AstOnp> {

    override fun handleToken(
        token: Token,
        astState: AstState<AstOnp>,
        exp: String,
    ): Boolean = if (token.value == "(") {
        astState.update {
            stack.addLast(token)
            this
        }
        true
    } else {
        false
    }
}

class ClosingParenthesisOnpTokenHandler : TokenHandler<AstOnp> {

    override fun handleToken(
        token: Token,
        astState: AstState<AstOnp>,
        exp: String,
    ): Boolean = if (token.value == ")") {
        var handled = false
        astState.update {
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
    } else {
        false
    }
}

class RegularOperatorOnpTokenHandler : TokenHandler<AstOnp> {

    override fun handleToken(
        token: Token,
        astState: AstState<AstOnp>,
        exp: String,
    ): Boolean = if (token.value in regularOperators()) {
        astState.update {
            while (stack.isNotEmpty() && operators()[token.value]!! <= operators()[stack.last().value]!!) {
                val element = stack.removeLast()
                output.add(element)
            }
            stack.addLast(token)
            this
        }
        true
    } else {
        false
    }

    private fun regularOperators(): List<String> =
        operators().keys.filter { it !in listOf("(", ")") }
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
            sendCurrentTokenToOutput()
            this
        }

        val tokenOperator = Token(index, ch.toString(), type = "operator")
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
            val readToken = currentReadToken
            currentReadToken =
                readToken?.copy(value = readToken.value + ch) ?: Token(index, ch.toString(), type = "literal")
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
        val currentReadToken = astState.value.currentReadToken
        if (currentReadToken != null) {

            if (!tokenHandler.handleToken(currentReadToken, astState, exp)) {
                // if not handled, so token is not operator, send to output as it is literal
                astState.update {
                    output.add(currentReadToken)
                    this
                }
            }
            astState.update {
                this.currentReadToken = null
                this
            }
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

fun AstOnp.sendCurrentTokenToOutput() {
    currentReadToken?.let { readToken ->
        output.add(readToken)
        currentReadToken = null
    }
}