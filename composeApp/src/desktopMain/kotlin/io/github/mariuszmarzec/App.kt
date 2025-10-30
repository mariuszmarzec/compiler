package io.github.mariuszmarzec

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.mariuszmarzec.logger.CompileReport
import io.github.mariuszmarzec.logger.ConsoleLogger
import io.github.mariuszmarzec.logger.InterceptingLogger
import io.github.mariuszmarzec.onp.onpKompiler
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun App() {
    MaterialTheme {
        Column(
            modifier = Modifier
                .safeContentPadding()
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            var text by remember { mutableStateOf("") }
            var output by remember { mutableStateOf("") }

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth().height(400.dp),
                value = text,
                onValueChange = { text = it },
            )

            Button(onClick = {
                output = ""
                val logger = InterceptingLogger(
                    logAction = { output += "$it\n" },
                    logger = ConsoleLogger()
                )
                val kompiler = onpKompiler(CompileReport(logger))
                val result = kompiler.compile(text).run()
                output += "Program output: $result\n"
            }) {
                Text("Compile")
            }

            Box(
                Modifier.verticalScroll(rememberScrollState())
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.LightGray)
            ) {
                Text(modifier = Modifier.fillMaxSize().padding(8.dp), text = output)
            }
        }
    }
}