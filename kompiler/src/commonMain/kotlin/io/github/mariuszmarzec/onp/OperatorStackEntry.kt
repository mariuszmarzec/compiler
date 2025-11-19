package io.github.mariuszmarzec.onp

import io.github.mariuszmarzec.kompiler.Operator
import io.github.mariuszmarzec.kompiler.Token

data class OperatorStackEntry(val token: Token, val operator: Operator)
