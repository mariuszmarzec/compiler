package io.github.mariuszmarzec.logger

interface Logger {
    fun log(message: String)
}

class ConsoleLogger : Logger {
    override fun log(message: String) {
        println(message)
    }
}

class InterceptingLogger(
    private val logAction: (String) -> Unit,
    private val logger: Logger
) : Logger {
    override fun log(message: String) {
        logAction(message)
        logger.log(message)
    }
}