package io.github.mariuszmarzec.onp

import io.github.mariuszmarzec.kompiler.AssignmentOperator
import io.github.mariuszmarzec.kompiler.AstState
import io.github.mariuszmarzec.kompiler.CodeBlockBracket
import io.github.mariuszmarzec.kompiler.EndOfLineOperator
import io.github.mariuszmarzec.kompiler.FunctionCall
import io.github.mariuszmarzec.kompiler.FunctionDeclaration
import io.github.mariuszmarzec.kompiler.FunctionDeclarationOperator
import io.github.mariuszmarzec.kompiler.Kompiler
import io.github.mariuszmarzec.kompiler.MathOperator
import io.github.mariuszmarzec.kompiler.Operator
import io.github.mariuszmarzec.kompiler.OperatorsTokenHandler
import io.github.mariuszmarzec.kompiler.Position
import io.github.mariuszmarzec.kompiler.SeparatorOperator
import io.github.mariuszmarzec.kompiler.Token
import io.github.mariuszmarzec.kompiler.TokenHandler
import io.github.mariuszmarzec.kompiler.TokenReader
import io.github.mariuszmarzec.kompiler.VariableDeclaration
import io.github.mariuszmarzec.kompiler.globalCompileReport
import io.github.mariuszmarzec.logger.CompileReport
import io.github.mariuszmarzec.logger.Report
import kotlin.math.pow

fun operators(): List<Operator> = listOf(
    CodeBlockBracket("{", -6, openClose = true),
    CodeBlockBracket("}", -5, openClose = true),
    AssignmentOperator("=", -4),
    AssignmentOperator("is", -4),
    FunctionDeclarationOperator("fun", -3),
    VariableDeclaration("val", -3),
    SeparatorOperator(",", -2),
    MathOperator("(", -1, openClose = true),
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
    EndOfLineOperator("\n", Int.MAX_VALUE),
    EndOfLineOperator(";", Int.MAX_VALUE),
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
        "fun" to SimpleOperatorHandler(operatorsMap.getValue("fun")),
        "=" to AssignmentOperatorHandler(operatorsMap.getValue("=")),
        "is" to AssignmentOperatorHandler(operatorsMap.getValue("is")),
        "\n" to EndOfLineHandler(operatorsMap.getValue("\n")),
        ";" to EndOfLineHandler(operatorsMap.getValue(";")),
        "{" to StartOfCodeBlockHandler(operatorsMap.getValue("{")),
        "}" to EndOfCodeBlockHandler(operatorsMap.getValue("}")),
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
    val processableStack: ArrayDeque<Processable> = ArrayDeque<Processable>().apply { addLast(BlockProcessable()) },
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
            if (value == null) {
                lastToken = field
            }
            field = value
        }

    override val value: AstOnp = this

    override fun update(action: AstOnp.() -> AstOnp) {
        println("AST before update: ${this.value.processableStack} operators: ${this.operatorsStack}")
        this.action()
    }

    override fun run(): String =
        value.processableStack.last().invoke(
            BlockProcessable(
                lineProcessables = emptyList(),
                variables = mapOf("magicnumber" to ConstVariableDeclaration("magicnumber", Primitive(56))),
                operators = operations,
                functionDeclarations = functionDeclarations
            )
        ).toString()

    fun intermediate(): String = printInput(output)
}

private fun printInput(input: MutableList<Token>): String = input.fold(StringBuilder()) { acc, item ->
    if (!acc.isEmpty()) {
        acc.append(" ")
    }
    acc.append(item.value)
}.toString()

fun onpKompiler(report: Report = globalCompileReport): Kompiler<AstOnp> {
    val tokenHandlers = OperatorsTokenHandler<AstOnp>(operatorHandlers().values.toList() + LiteralFunctionHandler())
    val handlers = listOf(LiteralReader(), OperatorReader(tokenHandlers, report), WhiteSpaceReader(tokenHandlers))
    return Kompiler<AstOnp>(handlers, { AstOnp(report = report) }, report) { ast ->
        ast.update {
            // clear any remaining read token
            currentReadToken?.let { readToken ->
                output.add(readToken)
                createNewProcessableSimpleNode(readToken)
                currentReadToken = null
            }

            flushStacks()
            this
        }
        ast

    }
}

class LiteralFunctionHandler() : TokenHandler<AstOnp> {
    override val operator: Operator = FunctionCall("", -1, 0)

    override fun handleToken(
        token: Token,
        astState: AstState<AstOnp>,
        exp: String,
    ): Boolean {
        if (astState.value.lastToken?.value in listOf("fun", "val")) {
            return false
        }
        val isLiteralFunction = astState.value.processableStack.any {
            (it as? BlockProcessable)?.operators?.values?.any { it.token == token.value } == true
        }
        return if (isLiteralFunction) {
            val element = OperatorStackEntry(token, FunctionCall(token.value, priority = 3, argumentsCount = 0))
            astState.update {
                stack.addLast(element)

                astState.value.operatorsStack.addLast(element)
                this
            }
            true
        } else {
            false
        }
    }
}

private fun AstOnp.flushStacks(tillOperator: Set<String> = emptySet()) {
    while (stack.isNotEmpty() && (tillOperator.isEmpty() || !tillOperator.contains(stack.last().operator.token))) {
        val token = stack.removeLast()
        if ((token.operator as? MathOperator)?.openClose == true) {
            report.error("Mismatched open close operator in expression: operator ${token.token.value} at position ${token.token.position}")
        }
        output.add(token.token)
    }
    // shunting yard specific
    while (operatorsStack.isNotEmpty() && (tillOperator.isEmpty() || !tillOperator.contains(operatorsStack.last().operator.token))) {
        val token = operatorsStack.removeLast()
        if ((token.operator as? MathOperator)?.openClose == true) {
            report.error("Mismatched open close operator in expression: operator ${token.token.value} at position ${token.token.position}")
        }
        makeProcessableNode(token)
    }
}

private fun AstOnp.makeProcessableNode() {
    makeProcessableNode(operatorsStack.removeLast())
}

private fun AstOnp.makeProcessableNode(token: OperatorStackEntry) {
    token.operator.makeProcessableNode(this, token.token)
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

            if (stack.lastOrNull()?.token?.value == "fun") {
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
            if (operatorsStack.lastOrNull()?.operator is FunctionDeclarationOperator) {
                if (lastToken?.value != ",") {
                    // if last token is not separator, increase arguments count
                    val funcDeclarationEntry = operatorsStack.removeLast()
                    val funcDeclarationOperator = funcDeclarationEntry.operator as FunctionDeclarationOperator
                    val updatedFuncDeclarationOperator =
                        funcDeclarationOperator.copy(argumentsCount = funcDeclarationOperator.argumentsCount + 1)
                    operatorsStack.addLast(
                        OperatorStackEntry(
                            funcDeclarationEntry.token,
                            updatedFuncDeclarationOperator
                        )
                    )
                }
                makeProcessableNode()
            }
            this
        }
        handled.also {
            if (!it) {
                compileReport.error("Mismatched parentheses at position ${token.position} in expression: $exp")
            }
        }
    } else {
        false
    }
}

class EndOfCodeBlockHandler(
    override val operator: Operator,
) : TokenHandler<AstOnp> {
    override fun handleToken(
        token: Token,
        astState: AstState<AstOnp>,
        exp: String
    ): Boolean = if (token.value == operator.token) {
        var handled = false
        astState.update {
            while (stack.isNotEmpty() && stack.last().token.value != "{") {
                output.add(stack.removeLast().token)
            }
            if (stack.isNotEmpty() && stack.last().token.value == "{") {
                stack.removeLast() // Remove the '('
                handled = true
            }

            // shunting yard specific
            while (operatorsStack.isNotEmpty() && operatorsStack.last().token.value != "{") {
                makeProcessableNode()
            }
            if (operatorsStack.isNotEmpty() && operatorsStack.last().token.value == "{") {
                operatorsStack.removeLast() // Remove the '{'
                operatorsStack.addLast(OperatorStackEntry(token, operator))
                makeProcessableNode()
                handled = true
            }
            this
        }
        handled.also {
            if (!it) {
                astState.value.report.error("Mismatched parentheses at position ${token.position} in expression: $exp")
            }
        }
    } else {
        false
    }


}

class StartOfCodeBlockHandler(
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
            makeProcessableNode(element)
            this
        }
        true
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
            if (operatorsStack.getOrNull(operatorsStack.lastIndex - 1)?.operator is FunctionDeclarationOperator) {
                val funcEntry = operatorsStack[operatorsStack.lastIndex - 1]
                val funcOperator = funcEntry.operator as FunctionDeclarationOperator
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
                report.error("Invalid assignment to variable declaration at position ${token.position} in expression: $exp")
            }
            stack.addLast(element)

            // shunting yard specific
            if (operatorsStack.lastOrNull()?.token?.value in listOf("val", "is")) {
                makeProcessableNode()
            } else {
                report.error("Invalid assignment to variable declaration at position ${token.position} in expression: $exp")
            }
            operatorsStack.addLast(element)
            this
        }
        true
    } else {
        false
    }
}

class EndOfLineHandler(override val operator: Operator) : TokenHandler<AstOnp> {

    override fun handleToken(
        token: Token,
        astState: AstState<AstOnp>,
        exp: String,
    ): Boolean = if (token.value == operator.token) {
        val element = OperatorStackEntry(token, operator)
        astState.update {
            flushStacks(tillOperator = setOf("{"))
            output.add(token.copy(value = "EOL"))

            // shunting yard specific
            operatorsStack.addLast(element)
            makeProcessableNode()
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
        position: Position,
        ch: Char,
        astState: AstState<AstOnp>
    ) {
        astState.handleCurrentTokenAsPossibleOperator(tokenHandler, exp)


        val tokenOperator = Token(position, ch.toString(), type = "operator")
        astState.update {
            val forShuntingYardToken = currentReadToken
            sendCurrentTokenToOutput()

            currentReadToken = tokenOperator

            createNewProcessableSimpleNode(forShuntingYardToken)
            this
        }

        if (!tokenHandler.handleToken(tokenOperator, astState, exp)) {
            compileReport.error("Lacking handling for Token: `${tokenOperator.value}` at position ${tokenOperator.position} in expression: $exp")
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
        position: Position,
        ch: Char,
        astState: AstState<AstOnp>
    ) {
        astState.update {
            val readToken = currentReadToken
            currentReadToken =
                readToken?.copy(value = readToken.value + ch) ?: Token(position, ch.toString(), type = "literal")
            this
        }
    }
}

class WhiteSpaceReader(private val tokenHandler: TokenHandler<AstOnp>) : TokenReader<AstOnp> {

    override val allowedCharacters: Set<Char>
        get() = setOf(' ', '\t')

    override fun readChar(
        exp: String,
        position: Position,
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

    fun invoke(context: BlockProcessable): Any
}

data class Primitive(val value: Int) : Processable {

    override fun invoke(context: BlockProcessable): Any = value
}

data class Expression(val operator: Operator, val left: Processable, val right: Processable) : Processable {

    override fun invoke(context: BlockProcessable): Any {
        val leftValue = dispatchVariable(left, context)
        val rightValue = dispatchVariable(right, context)
        return runOperation(leftValue, rightValue)
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

    override fun invoke(context: BlockProcessable): Any {
        return context.variables[name]?.value?.invoke(context)
            ?: throw IllegalStateException("Variable not found: $name")
    }
}

data class ConstVariableDeclaration(val name: String, val value: Processable? = null) : Processable {

    override fun invoke(context: BlockProcessable): Any = this
}

data class FunctionDeclarationProcessable(
    val name: String,
    val params: List<String> = listOf(),
    val bodyProcessable: BlockProcessable = BlockProcessable()
) : Processable {

    override fun invoke(context: BlockProcessable): Any {
        val functionContext = bodyProcessable.copy(
            variables = bodyProcessable.variables + context.variables // TODO how to handle params properly
        )
        return bodyProcessable.invoke(functionContext)
    }
}

data class FunctionCallProcessable(
    val call: FunctionCall,
    val arguments: List<Processable>
) : Processable {

    override fun invoke(context: BlockProcessable): Any {
        val function = context.functionDeclarations[call]
        return when (function) {
            is FunctionDeclaration.Function0 -> function.function()
            is FunctionDeclaration.Function1 -> function.function(dispatchVariable(arguments[0], context))
            is FunctionDeclaration.Function2 -> function.function(
                dispatchVariable(arguments[0], context),
                dispatchVariable(arguments[1], context),
            )

            is FunctionDeclaration.Function3 -> function.function(
                dispatchVariable(arguments[0], context),
                dispatchVariable(arguments[1], context),
                dispatchVariable(arguments[2], context),
            )

            null -> throw IllegalAccessError("Function not found: ${call.token} with ${call.argumentsCount} arguments")
        }
    }
}

data class BlockProcessable(
    val lineProcessables: List<Processable> = emptyList(),
    val variables: Map<String, ConstVariableDeclaration> = emptyMap(),
    val operators: Map<String, Operator> = emptyMap(),
    val functionDeclarations: Map<Operator, FunctionDeclaration> = emptyMap()
) : Processable {

    override fun invoke(context: BlockProcessable): Any {
        var result: Any = Unit

        var blockContext = this.copy(
            variables = this.variables + context.variables,
            operators = this.operators + context.operators,
            functionDeclarations = this.functionDeclarations + context.functionDeclarations
        )

        for (processable in lineProcessables) {
            result = processable.invoke(blockContext)
            when (result) {
                is ConstVariableDeclaration -> {
                    blockContext = blockContext.appendVariable(result)
                }

                is FunctionDeclarationProcessable -> {
                    blockContext = blockContext.appendFunction(result)
                }
            }
        }
        return result
    }

    fun appendProcessable(processable: Processable): BlockProcessable {
        return appendLine(processable)
    }

    private fun appendLine(processable: Processable): BlockProcessable {
        return this.copy(lineProcessables = this.lineProcessables + processable)
    }

    private fun appendVariable(variable: ConstVariableDeclaration): BlockProcessable {
        return this.copy(variables = this.variables + (variable.name to variable))
    }

    fun appendFunction(function: FunctionDeclarationProcessable): BlockProcessable {
        val new = when (function.params.size) {
            0 -> FunctionDeclaration.Function0(
                call = FunctionCall(function.name, priority = 0, argumentsCount = 0),
                function = {
                    function.invoke(this)
                }
            )

            1 -> FunctionDeclaration.Function1(
                call = FunctionCall(function.name, priority = 0, argumentsCount = 1),
                function = { arg1 ->
                    val funcContext = this.copy(
                        variables = this.variables + (function.params[0] to ConstVariableDeclaration(
                            function.params[0],
                            Primitive(arg1 as Int)
                        ))
                    )
                    function.invoke(funcContext)
                }
            )

            2 -> FunctionDeclaration.Function2(
                call = FunctionCall(function.name, priority = 0, argumentsCount = 2),
                function = { arg1, arg2 ->
                    val funcContext = this.copy(
                        variables = this.variables + mapOf(
                            function.params[0] to ConstVariableDeclaration(function.params[0], Primitive(arg1 as Int)),
                            function.params[1] to ConstVariableDeclaration(function.params[1], Primitive(arg2 as Int))
                        )
                    )
                    function.invoke(funcContext)
                }
            )

            else -> throw IllegalStateException("Function with more than 2 arguments not supported yet: ${function.name} with ${function.params.size} arguments")
        }
        val operator = FunctionCall(function.name, priority = 3, argumentsCount = function.params.size)
        return this.copy(
            operators = this.operators + (new.call.symbol to operator),
            functionDeclarations = functionDeclarations + (operator to new)
        )
    }
}

fun Operator.makeProcessableNode(ast: AstOnp, token: Token) = with(ast) {
    println("Making processable node for operator ${token.value} at position ${token.position}")
    when (val operator = this@makeProcessableNode) {
        is MathOperator -> {
            if (processableStack.size < 2) {
                throw IllegalStateException("Not enough elements in processable stack to apply operator ${token.value} at position ${token.position}")
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
                    report.error("Not enough elements in processable stack to apply function ${token.value} at position ${token.position}")
                }
                args.add(processableStack.removeLast())
            }
            args.reverse()
            processableStack.addLast(FunctionCallProcessable(operator, args))
        }

        is SeparatorOperator -> {
            if (operatorsStack.last().token.value == "(") {
                // do nothing, handled in closing parenthesis
            } else {
                report.error("Misplaced separator ${token.value} at position ${token.position}, should be inside function call parentheses")
            }
        }

        is VariableDeclaration -> {
            if (processableStack.size < 1) {
                report.error("Not enough elements in processable stack to apply variable declaration ${token.value} at position ${token.position}")
            }
            val processable = processableStack.removeLast()
            val variableName = when (processable) {
                is Literal -> processable.name
                else -> {
                    throw kotlin.IllegalStateException("Invalid variable name for declaration at position ${token.position}, expected literal but got $processable")
                }
            }

            val constVariableProcessable = ConstVariableDeclaration(variableName)
            processableStack.addLast(constVariableProcessable)
        }

        is AssignmentOperator -> {
            if (processableStack.size < 2) {
                report.error("Not enough elements in processable stack to apply assignment operator ${token.value} at position ${token.position}")
            }
            val valueProcessable = processableStack.removeLast()
            val variableProcessable = processableStack.removeLast()
            val declarationProcessable = variableProcessable as? ConstVariableDeclaration
                ?: throw IllegalStateException("Invalid variable for assignment at position ${token.position}, expected variable declaration but got $variableProcessable")

            val constVariableProcessable = declarationProcessable.copy(value = valueProcessable)
            processableStack.addLast(constVariableProcessable)
        }

        is EndOfLineOperator -> {
            println("End of line operator processing at position ${token.position}")
            if (processableStack.size < 2) {
                return@with
            }
            val processable = processableStack.removeLast()
            val blockProcessable = processableStack.removeLast().let {
              it as? BlockProcessable
                  ?: throw IllegalStateException("Invalid processable for end of line at position ${token.position}, expected block processable but got $it")
            }
            processableStack.addLast(blockProcessable.appendProcessable(processable))
        }

        is FunctionDeclarationOperator -> {
            val params = mutableListOf<String>()
            if (processableStack.size < operator.argumentsCount + 1) {
                report.error("Not enough elements in processable stack to apply function declaration ${token.value} at position ${token.position}")
            }
            repeat(operator.argumentsCount) {
                params.add(
                    (processableStack.removeLast() as? Literal)?.name
                        ?: throw IllegalStateException("Invalid function parameter name for declaration at position ${token.position}, expected literal but got $processableStack")
                )
            }

            val literalProcessable = processableStack.removeLast() as? Literal
                ?: throw IllegalStateException("Invalid function name for declaration at position ${token.position}, expected literal but got $processableStack")
            processableStack.addLast(
                FunctionDeclarationProcessable(
                    name = literalProcessable.name,
                    params = params,
                )
            )
        }

        is CodeBlockBracket -> {
            if (operator.token == "{") {
                processableStack.addLast(BlockProcessable())
            } else {
                if (processableStack.size < 2) {
                    report.error("Not enough elements in processable stack to apply code block at position ${token.position}")
                }
                val blockProcessable = processableStack.removeLast().let {
                    it as? BlockProcessable
                        ?: throw IllegalStateException("Invalid function body for declaration at position ${token.position}, expected block processable but got $it")
                }

                val functionDeclarationProcessable = processableStack.removeLast().let {
                    it as? FunctionDeclarationProcessable
                        ?: throw IllegalStateException("Invalid function for declaration at position ${token.position}, expected function declaration processable but got $it")
                }.copy(bodyProcessable = blockProcessable)

                val rootProcessable = processableStack.removeLast().let {
                    it as? BlockProcessable
                        ?: throw IllegalStateException("Invalid function body for declaration at position ${token.position}, expected block processable but got $it")
                }.appendFunction(functionDeclarationProcessable)

                processableStack.addLast(rootProcessable)
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
                astState.value.report.info("Handling as token '${currentReadToken.value}' at position ${currentReadToken.position} in expression: $exp: $it")
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

fun dispatchVariable(
    variable: Processable,
    context: BlockProcessable
): Int =
    if (variable is Literal) {
        context.variables[variable.name]?.value?.invoke(context) as? Int
            ?: throw IllegalStateException("Variable not found or not an integer: ${variable.name}")
    } else {
        variable.invoke(context) as? Int ?: throw IllegalStateException("Left operand is not an integer: $variable")
    }
