package io.github.mariuszmarzec.onp

import io.github.mariuszmarzec.kompiler.AstState
import io.github.mariuszmarzec.kompiler.FunctionCall
import io.github.mariuszmarzec.kompiler.FunctionDeclaration
import io.github.mariuszmarzec.kompiler.Kompiler
import io.github.mariuszmarzec.kompiler.Operator
import io.github.mariuszmarzec.kompiler.MathOperator
import io.github.mariuszmarzec.kompiler.OperatorsTokenHandler
import io.github.mariuszmarzec.kompiler.SeparatorOperator
import io.github.mariuszmarzec.kompiler.Token
import io.github.mariuszmarzec.kompiler.TokenHandler
import io.github.mariuszmarzec.kompiler.TokenReader
import io.github.mariuszmarzec.kompiler.globalCompileReport
import io.github.mariuszmarzec.logger.CompileReport
import io.github.mariuszmarzec.logger.Report
import kotlin.math.pow

fun operators(): List<Operator> = listOf(
    SeparatorOperator(",", -2),
    MathOperator("(", -1, openClose = true),
    MathOperator("-", 0),
    MathOperator("+", 1),
    MathOperator("plus", 1),
    MathOperator(")", 1, openClose = true),
    MathOperator("*", 2),
    MathOperator("times", 2),
    MathOperator("/", 2),
    MathOperator("%", 2),
    MathOperator("^", 3),
    FunctionCall("pow", 3, 2),
    FunctionCall("pow2", 3, 1),
    FunctionCall("pow", 3, 2),
    FunctionCall("min3", 3, 3),
)

fun operatorsMap(): Map<String, Operator> = operators().associateBy { it.symbol }

fun operatorHandlers(): Map<String, TokenHandler<AstOnp>> {
    val operatorsMap = operatorsMap()
    return mapOf(
        "," to SeparatorOperatorOnpTokenHandler(operatorsMap.getValue(",")),
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
        "pow" to FunctionTokenHandler(operatorsMap.getValue("pow")),
        "pow2" to FunctionTokenHandler(operatorsMap.getValue("pow2")),
        "min3" to FunctionTokenHandler(operatorsMap.getValue("min3")),
    )
}

private fun defaultFunctions(): MutableMap<Operator, FunctionDeclaration> {
    val operatorsMap = operatorsMap()
    return mutableMapOf(
        operatorsMap.getValue("pow") to FunctionDeclaration.Function2(operatorsMap.getValue("pow") as FunctionCall) { base, exponent ->
            (base as Int).toDouble().pow((exponent as Int).toDouble()).toInt()
        },
        operatorsMap.getValue("pow2") to FunctionDeclaration.Function1(operatorsMap.getValue("pow2") as FunctionCall) { base ->
            (base as Int) * base
        },
        operatorsMap.getValue("min3") to FunctionDeclaration.Function3(operatorsMap.getValue("min3") as FunctionCall) { a, b, c ->
            listOf(a as Int, b as Int, c as Int).min()
        },
    )
}

data class AstOnp(
    val output: MutableList<Token> = mutableListOf<Token>(),
    val stack: ArrayDeque<Token> = ArrayDeque<Token>(),
    var currentReadToken: Token? = null,
    // Shunting Yard specific
    val processableStack: ArrayDeque<Processable> = ArrayDeque<Processable>(),
    var operatorsStack: ArrayDeque<Token> = ArrayDeque<Token>(),
    val operators: MutableList<Operator> = operators().toMutableList(),
    val operations: MutableMap<String, Operator> = operatorsMap().toMutableMap(),
    val functionDeclarations: MutableMap<Operator, FunctionDeclaration> = defaultFunctions(),
    val report: Report,
) : AstState<AstOnp> {

    override val value: AstOnp = this

    override fun update(action: AstOnp.() -> AstOnp) {
        println("AST before update: ${this.value.processableStack} ${this.operatorsStack}")
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
    return Kompiler<AstOnp>(handlers, { AstOnp(report = report) }, report) { ast ->
        ast.update {
            // clear any remaining read token
            currentReadToken?.let { readToken ->
                output.add(readToken)
                createNewProcessableSimpleNode(readToken)
                currentReadToken = null
            }

            while (stack.isNotEmpty()) {
                val token = stack.removeLast()
                if ((operators.firstOrNull { token.value == it.symbol } as? MathOperator)?.openClose == true) {
                    report.error("Mismatched open close operator in expression: operator ${token.value} at index ${token.index}")
                }
                output.add(token)
            }

            // shunting yard specific
            while (operatorsStack.isNotEmpty()) {
                val token = operatorsStack.removeLast()
                if ((operators.firstOrNull { token.value == it.symbol } as? MathOperator)?.openClose == true) {
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
    operations[token.value]?.makeProcessableNode(this, token)
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
            if (stack.lastOrNull()?.value?.let { operations[it] is FunctionCall } == true) {
                output.add(stack.removeLast())
            }

            // shunting yard specific
            while (operatorsStack.isNotEmpty() && operatorsStack.last().value != "(") {
                makeProcessableNode()
            }
            if (operatorsStack.isNotEmpty() && operatorsStack.last().value == "(") {
                operatorsStack.removeLast() // Remove the '('
                handled = true
            }
            if (operatorsStack.lastOrNull()?.value?.let { operations[it] is FunctionCall } == true) {
                makeProcessableNode()
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

class SeparatorOperatorOnpTokenHandler(
    override val operator: Operator,
) : TokenHandler<AstOnp> {

    override fun handleToken(
        token: Token,
        astState: AstState<AstOnp>,
        exp: String,
    ): Boolean = if (token.value == operator.symbol) {
        astState.update {
            while (stack.isNotEmpty() && stack.last().value != "(") {
                output.add(stack.removeLast())
            }
            stack.addLast(token)

            // shunting yard specific
            while (operatorsStack.isNotEmpty() && operatorsStack.last().value != "(") {
                makeProcessableNode()
            }
            this
        }
        true
    } else {
        false
    }
}

class FunctionTokenHandler(
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
        astState.handleCurrentTokenAsPossibleOperator(tokenHandler, exp)


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
        astState.handleCurrentTokenAsPossibleOperator(tokenHandler, exp)
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

data class Expression(val operator: Operator, val left: Processable, val right: Processable) : Processable {

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

data class FunctionProcessable(
    val function: FunctionDeclaration,
    val arguments: List<Processable>
) : Processable {

    override fun run(): Any = when (function) {
        is FunctionDeclaration.Function1 -> function.function(arguments[0].run())
        is FunctionDeclaration.Function2 -> function.function(arguments[0].run(), arguments[1].run())
        is FunctionDeclaration.Function3 -> function.function(arguments[0].run(), arguments[1].run(), arguments[2].run())
    }
}

fun Operator.makeProcessableNode(ast: AstOnp, token: Token) = with(ast) {
    when (val operator = this@makeProcessableNode) {
        is MathOperator -> {
            if (processableStack.size < 2) {
                throw IllegalStateException("Not enough elements in processable stack to apply operator ${token.value} at index ${token.index}")
            }
            val right = processableStack.removeLast()
            val left = processableStack.removeLast()
            val expression = Expression(operations.getValue(token.value), left, right)
            processableStack.addLast(expression)
        }
        is FunctionCall -> {
            val args = mutableListOf<Processable>()
            repeat(argumentsCount) {
                if (processableStack.isEmpty()) {
                    report.error("Not enough elements in processable stack to apply function ${token.value} at index ${token.index}")
                }
                args.add(processableStack.removeLast())
            }
            args.reverse()
            val functionDeclaration = functionDeclarations[operator]
            if (functionDeclaration != null) {
                processableStack.addLast(FunctionProcessable(functionDeclaration, args))
            } else {
                report.error("Function declaration not found for function ${token.value} at index ${token.index}")
            }
        }
        is SeparatorOperator -> {
            if (operatorsStack.last().value == "(") {
                // do nothing, just separator between function arguments
            } else {
                report.error("Misplaced separator ${token.value} at index ${token.index}, should be inside function call parentheses")
            }
        }
    }
}

fun AstState<AstOnp>.handleCurrentTokenAsPossibleOperator(tokenHandler: TokenHandler<AstOnp>, exp: String) {
    val astState = this
    val currentReadToken = astState.value.currentReadToken
    if (currentReadToken != null) {

        val isToken = !tokenHandler.handleToken(currentReadToken, astState, exp)
            .also {
                astState.value.report.info("Handling as token '${currentReadToken.value}' at index ${currentReadToken.index} in expression: $exp: $it")
            }
        if (isToken) {
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
