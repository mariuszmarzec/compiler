package io.github.mariuszmarzec.onp

import io.github.mariuszmarzec.kompiler.AssignmentOperator
import io.github.mariuszmarzec.kompiler.AstState
import io.github.mariuszmarzec.kompiler.FunctionCall
import io.github.mariuszmarzec.kompiler.FunctionDeclaration
import io.github.mariuszmarzec.kompiler.Kompiler
import io.github.mariuszmarzec.kompiler.Operator
import io.github.mariuszmarzec.kompiler.MathOperator
import io.github.mariuszmarzec.kompiler.OperatorsTokenHandler
import io.github.mariuszmarzec.kompiler.SeparatorOperator
import io.github.mariuszmarzec.kompiler.VariableDeclaration
import io.github.mariuszmarzec.kompiler.Token
import io.github.mariuszmarzec.kompiler.TokenHandler
import io.github.mariuszmarzec.kompiler.TokenReader
import io.github.mariuszmarzec.kompiler.globalCompileReport
import io.github.mariuszmarzec.logger.CompileReport
import io.github.mariuszmarzec.logger.Report
import kotlin.math.pow

fun operators(): List<Operator> = listOf(
    AssignmentOperator("=", -4),
    AssignmentOperator("is", -4),
    VariableDeclaration("val", -3),
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
    FunctionCall("pow", 3, 1),
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
        "pow$1" to FunctionTokenHandler(operatorsMap.getValue("pow$1")),
        "pow$2" to FunctionTokenHandler(operatorsMap.getValue("pow$2")),
        "pow2$1" to FunctionTokenHandler(operatorsMap.getValue("pow2$1")),
        "min3$3" to FunctionTokenHandler(operatorsMap.getValue("min3$3")),
        "val" to SimpleOperatorHandler(operatorsMap.getValue("val")),
        "=" to AssignmentOperatorHandler(operatorsMap.getValue("=")),
        "is" to AssignmentOperatorHandler(operatorsMap.getValue("is")),
    )
}

private fun defaultFunctions(): MutableMap<Operator, FunctionDeclaration> {
    val operatorsMap = operatorsMap()
    return mutableMapOf(
        operatorsMap.getValue("pow$2") to FunctionDeclaration.Function2(operatorsMap.getValue("pow$2") as FunctionCall) { base, exponent ->
            (base as Int).toDouble().pow((exponent as Int).toDouble()).toInt()
        },
        operatorsMap.getValue("pow2$1") to FunctionDeclaration.Function1(operatorsMap.getValue("pow2$1") as FunctionCall) { base ->
            (base as Int) * base
        },
        operatorsMap.getValue("pow$1") to FunctionDeclaration.Function1(operatorsMap.getValue("pow$1") as FunctionCall) { base ->
            (base as Int) * base
        },
        operatorsMap.getValue("min3$3") to FunctionDeclaration.Function3(operatorsMap.getValue("min3$3") as FunctionCall) { a, b, c ->
            listOf(a as Int, b as Int, c as Int).min()
        },
    )
}

data class AstOnp(
    // ONP specific
    val output: MutableList<Token> = mutableListOf<Token>(),
    val stack: ArrayDeque<OperatorStackEntry> = ArrayDeque(),
    // Shunting Yard specific
    val processableStack: ArrayDeque<Processable> = ArrayDeque<Processable>(),
    var operatorsStack: ArrayDeque<OperatorStackEntry> = ArrayDeque(),
    val operators: MutableList<Operator> = operators().toMutableList(),
    val operations: MutableMap<String, Operator> = operatorsMap().toMutableMap(),
    val functionDeclarations: MutableMap<Operator, FunctionDeclaration> = defaultFunctions(),
    val report: Report,
) : AstState<AstOnp> {


    // common
    var lastToken: Token? = null

    var currentReadToken: Token? = null
        set(value) {
            field?.let { lastToken = it }
            field = value
        }

    override val value: AstOnp = this

    override fun update(action: AstOnp.() -> AstOnp) {
        println("AST before update: ${this.value.processableStack} ${this.operatorsStack}")
        this.action()
    }

    override fun run(): String = value.processableStack.last().invoke(BlockProcessable(emptyList(), emptyMap(), operatorsMap())).toString()

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
                if ((token.operator as? MathOperator)?.openClose == true) {
                    report.error("Mismatched open close operator in expression: operator ${token.token.value} at index ${token.token.index}")
                }
                output.add(token.token)
            }
            // shunting yard specific
            while (operatorsStack.isNotEmpty()) {
                val token = operatorsStack.removeLast()
                if ((token.operator as? MathOperator)?.openClose == true) {
                    report.error("Mismatched open close operator in expression: operator ${token.token.value} at index ${token.token.index}")
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

private fun AstOnp.makeProcessableNode(token: OperatorStackEntry) {
    operations[token.operator.symbol]?.makeProcessableNode(this, token.token)
}

// same as simple operator handler
class OpeningParenthesisOnpTokenHandler(
    override val operator: Operator,
) : TokenHandler<AstOnp> {

    override fun handleToken(
        token: Token,
        astState: AstState<AstOnp>,
        exp: String,
    ): Boolean = if (token.value == operator.token) {
        astState.update {
            stack.addLast(OperatorStackEntry(token, operator))

            operatorsStack.addLast(OperatorStackEntry(token, operator))
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
    ): Boolean = if (token.value == operator.token) {
        var handled = false
        astState.update {
            while (stack.isNotEmpty() && stack.last().token.value != "(") {
                output.add(stack.removeLast().token)
            }
            if (stack.isNotEmpty() && stack.last().token.value == "(") {
                stack.removeLast() // Remove the '('
                handled = true
            }
            if (stack.lastOrNull()?.token?.value?.let { operations[it] is FunctionCall } == true) {
                output.add(stack.removeLast().token)
            }

            // shunting yard specific
            while (operatorsStack.isNotEmpty() && operatorsStack.last().token.value != "(") {
                makeProcessableNode()
            }
            if (operatorsStack.isNotEmpty() && operatorsStack.last().token.value == "(") {
                operatorsStack.removeLast() // Remove the '('
                handled = true
            }
            if (operatorsStack.lastOrNull()?.operator is FunctionCall) {
                if (lastToken?.value != ",") {
                    // if last token is not separator, increase arguments count
                    val funcEntry = operatorsStack.removeLast()
                    val funcOperator = funcEntry.operator as FunctionCall
                    val updatedFuncOperator = funcOperator.copy(argumentsCount = funcOperator.argumentsCount + 1)
                    operatorsStack.addLast(OperatorStackEntry(funcEntry.token, updatedFuncOperator))
                }
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
    ): Boolean = if (token.value == operator.token) {
        astState.update {
            while (stack.isNotEmpty() && operator.priority <= operations.getValue(stack.last().token.value).priority) {
                val element = stack.removeLast()
                output.add(element.token)
            }
            stack.addLast(OperatorStackEntry(token, operator))
            currentReadToken = null
            this

            while (operatorsStack.isNotEmpty() && operator.priority <= operations.getValue(operatorsStack.last().token.value).priority) {
                makeProcessableNode()
            }
            operatorsStack.addLast(OperatorStackEntry(token, operator))
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
    ): Boolean = if (token.value == operator.token) {
        astState.update {
            while (stack.isNotEmpty() && stack.last().token.value != "(") {
                output.add(stack.removeLast().token)
            }
            stack.addLast(OperatorStackEntry(token, operator))

            // shunting yard specific
            while (operatorsStack.isNotEmpty() && operatorsStack.last().token.value != "(") {
                makeProcessableNode()
            }
            if (operatorsStack.getOrNull(operatorsStack.lastIndex - 1)?.operator is FunctionCall) {
                val funcEntry = operatorsStack[operatorsStack.lastIndex - 1]
                val funcOperator = funcEntry.operator as FunctionCall
                val updatedFuncOperator = funcOperator.copy(argumentsCount = funcOperator.argumentsCount + 1)
                operatorsStack[operatorsStack.lastIndex - 1] = OperatorStackEntry(funcEntry.token, updatedFuncOperator)
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
    ): Boolean = if (token.value == operator.token) {
        val element = OperatorStackEntry(token = token, operator = (operator as FunctionCall).copy(argumentsCount = 0))
        astState.update {
            stack.addLast(element)

            operatorsStack.addLast(element)
            this
        }
        true
    } else {
        false
    }
}

class SimpleOperatorHandler(
    override val operator: Operator,
) : TokenHandler<AstOnp> {

    override fun handleToken(
        token: Token,
        astState: AstState<AstOnp>,
        exp: String,
    ): Boolean = if (token.value == operator.token) {
        val element = OperatorStackEntry(token, operator)
        astState.update {
            stack.addLast(element)

            operatorsStack.addLast(element)
            this
        }
        true
    } else {
        false
    }
}

class AssignmentOperatorHandler(
    override val operator: Operator,
) : TokenHandler<AstOnp> {

    override fun handleToken(
        token: Token,
        astState: AstState<AstOnp>,
        exp: String,
    ): Boolean = if (token.value == operator.token) {
        val element = OperatorStackEntry(token, operator)
        astState.update {
            if (stack.lastOrNull()?.token?.value in listOf("val", "is")) {
                output.add(stack.removeLast().token)
            } else {
                report.error("Invalid assignment to variable declaration at index ${token.index} in expression: $exp")
            }
            stack.addLast(element)

            // shunting yard specific
            if (operatorsStack.lastOrNull()?.token?.value in listOf("val", "is")) {
                makeProcessableNode()
            } else {
                report.error("Invalid assignment to variable declaration at index ${token.index} in expression: $exp")
            }
            operatorsStack.addLast(element)
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
            forShuntingYardToken.value.toIntOrNull()?.let { Primitive(it) } ?: Literal(forShuntingYardToken.value)
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

    fun invoke(processable: BlockProcessable): Any
}

data class Primitive(val value: Int) : Processable {

    override fun invoke(processable: BlockProcessable): Any = value
}

data class Expression(val operator: Operator, val left: Processable, val right: Processable) : Processable {

    override fun invoke(processable: BlockProcessable): Any {
        val leftValue = left.invoke(processable)
        val rightValue = right.invoke(processable)
        return if (leftValue is Int && rightValue is Int) {
            runOperation(leftValue, rightValue)
        } else {
            "($leftValue ${operator.token} $rightValue)"
        }
    }

    private fun runOperation(left: Int, right: Int): Any {
        return when (operator.token) {
            "+", "plus" -> left + right
            "-" -> left - right
            "*", "times" -> left * right
            "/" -> left / right
            "%" -> left % right
            "^" -> left.toDouble().pow(right.toDouble()).toInt()
            else -> throw IllegalStateException("Unknown operator: ${operator.token}")
        }
    }
}

data class Literal(val name: String) : Processable {

    override fun invoke(processable: BlockProcessable): Any = name
}

data class ConstVariableDeclaration(val name: String, val value: Processable? = null) : Processable {

    override fun invoke(processable: BlockProcessable): Any = this
}

data class FunctionProcessable(
    val function: FunctionDeclaration,
    val arguments: List<Processable>
) : Processable {

    override fun invoke(processable: BlockProcessable): Any = when (function) {
        is FunctionDeclaration.Function1 -> function.function(arguments[0].invoke(processable))
        is FunctionDeclaration.Function2 -> function.function(
            arguments[0].invoke(processable),
            arguments[1].invoke(processable)
        )

        is FunctionDeclaration.Function3 -> function.function(
            arguments[0].invoke(processable), arguments[1].invoke(processable), arguments[2].invoke(
                processable
            )
        )
    }
}

data class BlockProcessable(
    val processables: List<Processable>,
    val variables: Map<String, ConstVariableDeclaration> = emptyMap(),
    val operators: Map<String, Operator> = emptyMap(),
) : Processable {

    override fun invoke(processable: BlockProcessable): Any {
        var result: Any = Unit
        for (processable in processables) {
            result = processable.invoke(this)
        }
        return result
    }

    fun appendProcessable(processable: Processable): BlockProcessable {
        return this.copy(processables = this.processables + processable)
    }

    fun appendVariable(variable: ConstVariableDeclaration): BlockProcessable {
        return this.copy(variables = this.variables + (variable.name to variable))
    }

    fun appendOperator(operator: Operator): BlockProcessable {
        return this.copy(operators = this.operators + (operator.symbol to operator))
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
            println("Function call for ${token.value} with arguments count ${operator.argumentsCount}")
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
            if (operatorsStack.last().token.value == "(") {
                // do nothing, just separator between function arguments
            } else {
                report.error("Misplaced separator ${token.value} at index ${token.index}, should be inside function call parentheses")
            }
        }

        is VariableDeclaration -> {
            if (processableStack.size < 1) {
                report.error("Not enough elements in processable stack to apply variable declaration ${token.value} at index ${token.index}")
            }
            val processable = processableStack.removeLast()
            val variableName = when (processable) {
                is Literal -> processable.name
                else -> {
                    throw kotlin.IllegalStateException("Invalid variable name for declaration at index ${token.index}, expected literal but got $processable")
                }
            }

            val constVariableProcessable = ConstVariableDeclaration(variableName)
            processableStack.addLast(constVariableProcessable)
        }

        is AssignmentOperator -> {
            if (processableStack.size < 2) {
                report.error("Not enough elements in processable stack to apply assignment operator ${token.value} at index ${token.index}")
            }
            val valueProcessable = processableStack.removeLast()
            val variableProcessable = processableStack.removeLast()
            val declarationProcessable = variableProcessable as? ConstVariableDeclaration
                ?: throw IllegalStateException("Invalid variable for assignment at index ${token.index}, expected variable declaration but got $variableProcessable")

            val constVariableProcessable = declarationProcessable.copy(value = valueProcessable)
            processableStack.addLast(constVariableProcessable)
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
