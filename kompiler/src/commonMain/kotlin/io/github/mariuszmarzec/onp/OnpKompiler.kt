package io.github.mariuszmarzec.onp

import io.github.mariuszmarzec.kompiler.AstState
import io.github.mariuszmarzec.kompiler.Kompiler
import io.github.mariuszmarzec.kompiler.Operator
import io.github.mariuszmarzec.kompiler.OperatorsTokenHandler
import io.github.mariuszmarzec.kompiler.Token
import io.github.mariuszmarzec.kompiler.TokenHandler
import io.github.mariuszmarzec.kompiler.TokenReader
import io.github.mariuszmarzec.kompiler.globalCompileReport
import io.github.mariuszmarzec.logger.CompileReport

val operators: List<Operator> = listOf(
    Operator("(", -1),
    Operator("-", 0),
    Operator("+", 1),
    Operator("plus", 1),
    Operator(")", 1),
    Operator("*", 2),
    Operator("times", 2),
    Operator("/", 2),
    Operator("%", 2),
    Operator("^", 3),
)

val operatorsMap: Map<String, Operator> = operators.associateBy { it.symbol }

val onpTokensHandlers: List<TokenHandler<AstOnp>> = operatorHandlers().values.toList()

fun operatorHandlers() = mapOf(
    "(" to OpeningParenthesisOnpTokenHandler(operatorsMap.getValue("(")),
    "-" to RegularOperatorOnpTokenHandler(operatorsMap.getValue("-")),
    "+" to RegularOperatorOnpTokenHandler(operatorsMap.getValue("+")),
    "plus" to RegularOperatorOnpTokenHandler(operatorsMap.getValue("plus")),
    ")" to ClosingParenthesisOnpTokenHandler(operatorsMap.getValue(")")),
    "*" to RegularOperatorOnpTokenHandler(operatorsMap.getValue("*")),
    "times" to RegularOperatorOnpTokenHandler(operatorsMap.getValue("times")),
    "/" to RegularOperatorOnpTokenHandler(operatorsMap.getValue("/")),
    "%" to RegularOperatorOnpTokenHandler(operatorsMap.getValue("%")),
    "^" to RegularOperatorOnpTokenHandler(operatorsMap.getValue("^")),
)



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

    override fun print(): String = printInput(output)
}

private fun printInput(input: MutableList<Token>): String = input.fold(StringBuilder()) { acc, item ->
    if (!acc.isEmpty()) {
        acc.append(" ")
    }
    acc.append(item.value)
}.toString()

fun onpKompiler(): Kompiler<AstOnp> {
    val tokenHandlers = OperatorsTokenHandler<AstOnp>(onpTokensHandlers)
    val handlers = listOf(LiteralReader(), OperatorReader(tokenHandlers, globalCompileReport), WhiteSpaceReader(tokenHandlers))
    return Kompiler<AstOnp>(handlers, { AstOnp() }, globalCompileReport) { ast ->
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
        ast
    }
}

class OpeningParenthesisOnpTokenHandler(
    override val operator: Operator,
) : TokenHandler<AstOnp> {

    override fun handleToken(
        token: Token,
        astState: AstState<AstOnp>,
        exp: String,
    ): Boolean = if (token.value == operator.symbol) {
        astState.update {
            stack.addLast(token)
            this
        }
        true
    } else {
        false
    }
}

class ClosingParenthesisOnpTokenHandler(
    override val operator: Operator,
    private val compileReport: CompileReport = globalCompileReport,
) : TokenHandler<AstOnp> {

    override fun handleToken(
        token: Token,
        astState: AstState<AstOnp>,
        exp: String,
    ): Boolean = if (token.value == operator.symbol) {
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
        handled.also {
            if (!it) {
                compileReport.error("Mismatched parentheses at index ${token.index} in expression: $exp")
            }
        }
    } else {
        false
    }
}

class RegularOperatorOnpTokenHandler(
    override val operator: Operator,
) : TokenHandler<AstOnp> {

    private val operators: Map<String, Operator> = operatorsMap

    override fun handleToken(
        token: Token,
        astState: AstState<AstOnp>,
        exp: String,
    ): Boolean = if (token.value == operator.symbol) {
        astState.update {
            while (stack.isNotEmpty() && operator.priority <= operators.getValue(stack.last().value).priority) {
                val element = stack.removeLast()
                output.add(element)
            }
            stack.addLast(token)
            currentReadToken = null
            this
        }
        true
    } else {
        false
    }
}

class OperatorReader(
    private val tokenHandler: TokenHandler<AstOnp>,
    private val compileReport: CompileReport
) : TokenReader<AstOnp> {

    override val allowedCharacters: Set<Char>
        get() = operatorHandlers().keys.flatMap { it.split("").mapNotNull { it.firstOrNull() } }.toSet()

    override fun readChar(
        exp: String,
        index: Int,
        ch: Char,
        astState: AstState<AstOnp>
    ) {
        val tokenOperator = Token(index, ch.toString(), type = "operator")
        astState.update {
            sendCurrentTokenToOutput()
            currentReadToken = tokenOperator
            this
        }

        if (!tokenHandler.handleToken(tokenOperator, astState, exp)) {
            compileReport.error("Lacking handling for Token: `${tokenOperator.value}` at index ${tokenOperator.index} in expression: $exp")
        }
        astState.update {
            currentReadToken = null
            this
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


fun AstOnp.sendCurrentTokenToOutput() {
    currentReadToken?.let { readToken ->
        output.add(readToken)
        currentReadToken = null
    }
}