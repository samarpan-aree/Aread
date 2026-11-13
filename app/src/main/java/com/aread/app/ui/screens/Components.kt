package com.aread.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.aread.app.ui.theme.AreadColors

@Composable
fun PrimaryButton(text: String, enabled: Boolean = true, loading: Boolean = false, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = AreadColors.Paper)
        else Text(text)
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, AreadColors.Line),
    ) { Text(text, color = AreadColors.Ink) }
}

@Composable
fun Field(
    value: String, onChange: (String) -> Unit, label: String,
    keyboard: KeyboardType = KeyboardType.Text, secret: Boolean = false, modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) },
        singleLine = true, modifier = modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = AreadColors.Line, focusedBorderColor = AreadColors.Accent,
            unfocusedContainerColor = AreadColors.Surface, focusedContainerColor = AreadColors.Surface,
        ),
    )
}

@Composable
fun ChoiceChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val bg = if (selected) AreadColors.AccentSoft else AreadColors.Surface
    val line = if (selected) AreadColors.Accent else AreadColors.Line
    Box(
        modifier
            .border(1.dp, line, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .then(Modifier.padding(0.dp)),
    ) {
        Surface(color = bg, shape = RoundedCornerShape(12.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                if (selected) Text("✓", color = AreadColors.Accent)
            }
        }
    }
}

@Composable
fun ScreenHeader(title: String, subtitle: String? = null) {
    Column(Modifier.padding(bottom = 20.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        if (subtitle != null) {
            Spacer(Modifier.height(6.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = AreadColors.Muted)
        }
    }
}
