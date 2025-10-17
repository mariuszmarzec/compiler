package io.github.mariuszmarzec

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview

import compiler.composeapp.generated.resources.Res
import compiler.composeapp.generated.resources.compose_multiplatform
import io.github.mariuszmarzec.onp.onpKompiler

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

            Button(onClick = { output = onpKompiler().compile(text) }) {
                Text("Compile")
            }

            Box(Modifier.fillMaxWidth().weight(1f).background(Color.LightGray)) {
                Text(modifier = Modifier.fillMaxSize().padding(8.dp), text = output)
            }
        }
    }
}