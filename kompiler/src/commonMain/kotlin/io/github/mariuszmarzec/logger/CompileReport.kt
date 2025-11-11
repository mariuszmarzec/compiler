package io.github.mariuszmarzec.logger

interface Report {
    fun info(message: String)
    fun warning(message: String)
    fun error(message: String)
}

class CompileReport(private val logger: Logger = ConsoleLogger()) : Report {

    override fun info(message: String) {
        logger.log("O: $message")
    }

    override fun warning(message: String) {
        logger.log("W: $message")
    }

    override fun error(message: String) {
        logger.log("E: $message")
        throw IllegalStateException(message)
    }
}

