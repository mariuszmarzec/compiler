package io.github.mariuszmarzec.onp

import io.github.mariuszmarzec.kompiler.AstState
import io.github.mariuszmarzec.kompiler.Kompiler
import io.github.mariuszmarzec.kompiler.Operation
import io.github.mariuszmarzec.kompiler.Operator
import io.github.mariuszmarzec.kompiler.OperatorsTokenHandler
import io.github.mariuszmarzec.kompiler.Token
import io.github.mariuszmarzec.kompiler.TokenHandler
import io.github.mariuszmarzec.kompiler.TokenReader
import io.github.mariuszmarzec.kompiler.globalCompileReport
import io.github.mariuszmarzec.logger.CompileReport
import io.github.mariuszmarzec.logger.Report
import kotlin.math.pow

fun operators(): List<Operator> = listOf(
    Operator("(", -1, openClose = true),
    Operator("-", 0),
    Operator("+", 1),
    Operator("plus", 1),
    Operator(")", 1, openClose = true),
    Operator("*", 2),
    Operator("times", 2),
    Operator("/", 2),
    Operator("%", 2),
    Operator("^", 3),
)

fun operatorsMap(): Map<String, Operator> = operators().associateBy { it.symbol }

fun operatorHandlers(): Map<String, TokenHandler<AstOnp>> {
    val operatorsMap = operatorsMap()
    return mapOf(
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
}


data class AstOnp(
    val output: MutableList<Token> = mutableListOf<Token>(),
    val stack: ArrayDeque<Token> = ArrayDeque<Token>(),
    var currentReadToken: Token? = null,
    // Shunting Yard specific
    val processableStack: ArrayDeque<Processable> = ArrayDeque<Processable>(),
    var operatorsStack: ArrayDeque<Token> = ArrayDeque<Token>(),
    val operators: MutableList<Operation> = operators().toMutableList(),
    val operations: MutableMap<String, Operation> = operatorsMap().toMutableMap()
) : AstState<AstOnp> {

    override val value: AstOnp = this

    override fun update(action: AstOnp.() -> AstOnp) {
        this.action()
    }

    override fun run(): String = value.processableStack.last().run().toString()

    fun intermediate(): String = printInput(output)
}

private fun printInput(input: MutableList<Token>): String = input.fold(StringBuilder()) { acc, item ->
    if (!acc.isEmpty()) {
        acc.append(" ")
    }
    acc.append(item.value)
}.toString()

fun onpKompiler(report: Report = globalCompileReport): Kompiler<AstOnp> {
    val tokenHandlers = OperatorsTokenHandler<AstOnp>(operatorHandlers().values.toList())
    val handlers = listOf(LiteralReader(), OperatorReader(tokenHandlers, report), WhiteSpaceReader(tokenHandlers))
    return Kompiler<AstOnp>(handlers, { AstOnp() }, report) { ast ->
        ast.update {
            // clear any remaining read token
            currentReadToken?.let { readToken ->
                output.add(readToken)
                createNewProcessableSimpleNode(readToken)
                currentReadToken = null
            }

            while (stack.isNotEmpty()) {
                val token = stack.removeLast()
                if ((operators.firstOrNull { token.value == it.symbol } as? Operator)?.openClose == true) {
                    report.error("Mismatched open close operator in expression: operator ${token.value} at index ${token.index}")
                }
                output.add(token)
            }

            // shunting yard specific
            while (operatorsStack.isNotEmpty()) {
                val token = operatorsStack.removeLast()
                if ((operators.firstOrNull { token.value == it.symbol } as? Operator)?.openClose == true) {
                    report.error("Mismatched open close operator in expression: operator ${token.value} at index ${token.index}")
                }
                makeProcessableNode(token)
            }
            this
        }
        ast
    }
}

private fun AstOnp.makeProcessableNode() {
    makeProcessableNode(operatorsStack.removeLast())
}

private fun AstOnp.makeProcessableNode(token: Token) {
    if (processableStack.size < 2) {
        throw IllegalStateException("Not enough elements in processable stack to apply operator ${token.value} at index ${token.index}")
    }
    val right = processableStack.removeLast()
    val left = processableStack.removeLast()
    val expression = Expression(operations.getValue(token.value), left, right)
    processableStack.addLast(expression)
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

            operatorsStack.addLast(token)
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

            // shunting yard specific
            while (operatorsStack.isNotEmpty() && operatorsStack.last().value != "(") {
                makeProcessableNode()
            }
            if (operatorsStack.isNotEmpty() && operatorsStack.last().value == "(") {
                operatorsStack.removeLast() // Remove the '('
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

    override fun handleToken(
        token: Token,
        astState: AstState<AstOnp>,
        exp: String,
    ): Boolean = if (token.value == operator.symbol) {
        astState.update {
            while (stack.isNotEmpty() && operator.priority <= operations.getValue(stack.last().value).priority) {
                val element = stack.removeLast()
                output.add(element)
            }
            stack.addLast(token)
            currentReadToken = null
            this

            while (operatorsStack.isNotEmpty() && operator.priority <= operations.getValue(operatorsStack.last().value).priority) {
                makeProcessableNode()
            }
            operatorsStack.addLast(token)
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
    private val compileReport: Report
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
            val forShuntingYardToken = currentReadToken
            sendCurrentTokenToOutput()

            currentReadToken = tokenOperator

            createNewProcessableSimpleNode(forShuntingYardToken)
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

private fun AstOnp.createNewProcessableSimpleNode(forShuntingYardToken: Token?) {
    if (forShuntingYardToken != null) {
        processableStack.addLast(
            forShuntingYardToken.value.toIntOrNull()?.let { Primitive(it) } ?: Variable(forShuntingYardToken.value)
        )
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

                    createNewProcessableSimpleNode(currentReadToken)
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

interface Processable {

    fun run(): Any
}

data class Primitive(val value: Int) : Processable {

    override fun run(): Any = value
}

data class Expression(val operator: Operation, val left: Processable, val right: Processable) : Processable {

    override fun run(): Any {
        val leftValue = left.run()
        val rightValue = right.run()
        return if (leftValue is Int && rightValue is Int) {
            runOperation(leftValue, rightValue)
        } else {
            "($leftValue ${operator.symbol} $rightValue)"
        }
    }

    private fun runOperation(left: Int, right: Int): Any {
        return when (operator.symbol) {
            "+", "plus" -> left + right
            "-" -> left - right
            "*", "times" -> left * right
            "/" -> left / right
            "%" -> left % right
            "^" -> left.toDouble().pow(right.toDouble()).toInt()
            else -> throw IllegalStateException("Unknown operator: ${operator.symbol}")
        }
    }
}

data class Variable(val name: String) : Processable {

    override fun run(): Any = name
}