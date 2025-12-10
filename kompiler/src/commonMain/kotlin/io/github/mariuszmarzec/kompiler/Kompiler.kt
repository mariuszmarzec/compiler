package io.github.mariuszmarzec.kompiler

import io.github.mariuszmarzec.logger.CompileReport
import io.github.mariuszmarzec.logger.Report
val globalCompileReport: CompileReport = CompileReport()


interface AstState<T> {

    val value: T

    fun update(action: T.() -> T)

    fun run(): String
}

class FailAstState<T> : AstState<T> {
    override val value: T
        get() = throw IllegalAccessError("Not supported")

    override fun update(action: T.() -> T) {
        // no op
    }

    override fun run(): String = "-1"
}


class Kompiler<AST>(
    private val readers: List<TokenReader<AST>>,
    private val astBuilder: () -> AstState<AST>,
    private val compileReport: Report,
    private val finisher: (AstState<AST>) -> AstState<AST> = { FailAstState() },
) {

    fun compile(exp: String): AstState<AST> =
        try {
            var line = 0
            var ast = astBuilder()
            exp.let { if (exp.last() == '\n') exp else exp + '\n' }
                .forEachIndexed { index, ch ->
                val position = Position(index, line, index - exp.lastIndexOf('\n', index - 1) )
                readers.firstOrNull { it.allowedCharacters.contains(ch) }
                    ?.readChar(exp, position, ch, ast)
                    ?: compileReport.error("Unexpected character '$ch' at index $index in expression: $exp")

                if (ch == '\n') {
                    line++
                }
            }
            finisher(ast)
        } catch (t: Throwable) {
            compileReport.warning("Error while evaluating expression: ${t.message}")
            FailAstState()
        }
}

data class Token(
    val position: Position,
    val value: String,
    val type: String
)

interface TokenReader<AST> {

    val allowedCharacters: Set<Char>

    fun readChar(
        exp: String,
        position: Position,
        ch: Char,
        ast: AstState<AST>
    )
}

interface TokenHandler<AST> {

    val operator: Operator

    fun handleToken(token: Token, ast: AstState<AST>, exp: String): Boolean
}

class OperatorsTokenHandler<T>(
    private val handlers: List<TokenHandler<T>>,
) : TokenHandler<T> {

    override val operator: Operator
        get() = throw IllegalAccessError("Not supported")

    override fun handleToken(
        token: Token,
        astState: AstState<T>,
        exp: String,
    ): Boolean = handlers.firstOrNull { it.handleToken(token, astState, exp) }?.let { true } == true
}

sealed interface Operator {
    val token: String
    val symbol: String
    val priority: Int
}

data class MathOperator(
    override val token: String,
    override val priority: Int,
    val openClose: Boolean = false,
) : Operator {
    override val symbol: String = token
}

data class FunctionCall(
    override val token: String,
    override val priority: Int,
    val argumentsCount: Int,
) : Operator {
    override val symbol: String = "$token$$argumentsCount"
}

data class AssignmentOperator(
    override val token: String,
    override val priority: Int,
) : Operator {
    override val symbol: String = token
}

data class SeparatorOperator(
    override val token: String,
    override val priority: Int,
) : Operator {
    override val symbol: String = token
}

data class VariableDeclaration(
    override val token: String,
    override val priority: Int,
) : Operator {
    override val symbol: String = token
}

data class FunctionDeclarationOperator(
    override val token: String,
    override val priority: Int,
) : Operator {
    override val symbol: String = token
}

sealed class FunctionDeclaration(
    open val call: FunctionCall
) {
    data class Function1(
        override val call: FunctionCall,
        val function: (Any) -> Any
    ) : FunctionDeclaration(call)

    data class Function2(
        override val call: FunctionCall,
        val function: (Any, Any) -> Any
    ) : FunctionDeclaration(call)

    data class Function3(
        override val call: FunctionCall,
        val function: (Any, Any, Any) -> Any
    ) : FunctionDeclaration(call)
}

data class EndOfLineOperator(
    override val token: String = "\n",
    override val priority: Int = Int.MAX_VALUE,
) : Operator {
    override val symbol: String = token
}

data class Position(
    val index: Int,
    val line: Int,
    val column: Int
)