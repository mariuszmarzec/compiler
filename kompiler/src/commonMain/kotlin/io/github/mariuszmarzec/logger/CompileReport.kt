package io.github.mariuszmarzec.logger

interface Report {
    fun warning(message: String)
    fun error(message: String)
}

class CompileReport(private val logger: Logger = ConsoleLogger()) : Report {

    override fun warning(message: String) {
        logger.log("W: $message")
    }

    override fun error(message: String) {
        logger.log("E: $message")
        throw IllegalStateException(message)
    }
}

