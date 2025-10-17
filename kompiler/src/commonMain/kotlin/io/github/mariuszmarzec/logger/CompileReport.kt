package io.github.mariuszmarzec.logger

class CompileReport {

    fun warning(message: String) {
        println("W: $message")
    }

    fun error(message: String) {
        println("E: $message")
        throw IllegalStateException(message)
    }
}
