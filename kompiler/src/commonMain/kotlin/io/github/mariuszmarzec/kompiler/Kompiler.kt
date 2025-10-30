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
            var ast = astBuilder()
            exp.forEachIndexed { index, ch ->
                readers.firstOrNull { it.allowedCharacters.contains(ch) }
                    ?.readChar(exp, index, ch, ast)
                    ?: compileReport.error("Unexpected character '$ch' at index $index in expression: $exp")
            }
            finisher(ast)
        } catch (t: Throwable) {
            compileReport.warning("Error while evaluating expression: ${t.message}")
            FailAstState()
        }
}

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

sealed interface Operation {
    val symbol: String
    val priority: Int
}

data class Operator(
    override val symbol: String,
    override val priority: Int,
    val openClose: Boolean = false,
) : Operation

data class Function(
    override val symbol: String,
    override val priority: Int,
    val argumentsCount: Int,
    val arguments: List<Int>,
) : Operation