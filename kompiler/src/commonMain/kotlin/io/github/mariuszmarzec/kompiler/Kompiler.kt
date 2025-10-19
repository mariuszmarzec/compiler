package io.github.mariuszmarzec.kompiler

import io.github.mariuszmarzec.logger.CompileReport

val globalCompileReport: CompileReport = CompileReport()

interface AstState<T> {

    val value: T

    fun update(action: T.() -> T)

    fun print(): String
}

class Kompiler<AST>(
    private val readers: List<TokenReader<AST>>,
    private val astBuilder: () -> AstState<AST>,
    private val compileReport: CompileReport,
    private val finisher: (AstState<AST>) -> AstState<AST> = { it },
) {

    fun compile(exp: String): String {
        var ast = astBuilder()
        exp.forEachIndexed { index, ch ->
            readers.firstOrNull { it.allowedCharacters.contains(ch) }
                ?.readChar(exp, index, ch, ast)
                ?: compileReport.error("Unexpected character '$ch' at index $index in expression: $exp")
        }
        // EOF READER
        ast = finisher(ast)
        return ast.print()
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

data class Operator(
    val symbol: String,
    val priority: Int,
    val openClose: Boolean = false,
)
