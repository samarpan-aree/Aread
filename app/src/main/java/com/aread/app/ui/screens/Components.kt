package com.aread.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.aread.app.data.Countries
import com.aread.app.data.Country
import com.aread.app.tutor.Mouth
import com.aread.app.ui.theme.AreadColors
import com.aread.app.ui.theme.LocalPalette
import com.aread.app.ui.theme.ThemeState
import java.io.File

@Composable
fun imeVisible(): Boolean = WindowInsets.ime.getBottom(LocalDensity.current) > 0

@Composable
fun PrimaryButton(text: String, enabled: Boolean = true, loading: Boolean = false, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = Modifier.fillMaxWidth().height(54.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = AreadColors.Accent,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = AreadColors.Line,
            disabledContentColor = AreadColors.Muted,
        ),
    ) {
        if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
        else Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun SecondaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(54.dp),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, AreadColors.Line),
    ) { Text(text, color = if (enabled) AreadColors.Ink else AreadColors.Muted, style = MaterialTheme.typography.titleMedium) }
}

@Composable
fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    keyboard: KeyboardType = KeyboardType.Text,
    secret: Boolean = false,
    modifier: Modifier = Modifier,
    maxLen: Int? = null,
    keep: ((Char) -> Boolean)? = null,
    suffix: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { v ->
            var out = v
            if (keep != null) out = out.filter(keep)
            if (maxLen != null) out = out.take(maxLen)
            onChange(out)
        },
        label = { Text(label) },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        suffix = if (suffix != null) ({ Text(suffix, color = AreadColors.Muted) }) else null,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = AreadColors.Line,
            focusedBorderColor = AreadColors.Accent,
            unfocusedContainerColor = AreadColors.Surface,
            focusedContainerColor = AreadColors.Surface,
            focusedLabelColor = AreadColors.Accent,
            cursorColor = AreadColors.Accent,
        ),
    )
}

@Composable
fun DigitField(value: String, onChange: (String) -> Unit, label: String, maxLen: Int, modifier: Modifier = Modifier, suffix: String? = null) {
    Field(value, onChange, label, KeyboardType.Number, modifier = modifier, maxLen = maxLen, keep = { it.isDigit() }, suffix = suffix)
}

@Composable
fun PhoneField(
    country: Country,
    onCountry: (Country) -> Unit,
    number: String,
    onNumber: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .height(56.dp)
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, AreadColors.Line, RoundedCornerShape(14.dp))
                .background(AreadColors.Surface)
                .clickable { open = true }
                .padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(country.flag, fontSize = 22.sp)
            Spacer(Modifier.width(6.dp))
            Text("+${country.dial}", style = MaterialTheme.typography.bodyLarge)
            Icon(Icons.Outlined.ArrowDropDown, null, tint = AreadColors.Muted)
        }
        Spacer(Modifier.width(8.dp))
        Field(
            value = number,
            onChange = { onNumber(it.take(country.maxDigits)) },
            label = "Phone number",
            keyboard = KeyboardType.Phone,
            modifier = Modifier.weight(1f),
            maxLen = country.maxDigits,
            keep = { it.isDigit() },
        )
    }
    if (open) {
        CountryDialog(
            onPick = { onCountry(it); onNumber(number.take(it.maxDigits)); open = false },
            onDismiss = { open = false },
        )
    }
}

@Composable
private fun CountryDialog(onPick: (Country) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val list = remember(query) {
        val q = query.trim().removePrefix("+")
        if (q.isEmpty()) Countries.all
        else Countries.all.filter { it.name.contains(q, true) || it.dial.startsWith(q) || it.iso.equals(q, true) }
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(AreadColors.Surface)
                .padding(16.dp),
        ) {
            Text("Choose country", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                placeholder = { Text("Search country or code") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn {
                items(list, key = { it.iso }) { c ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(c) }.padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(c.flag, fontSize = 24.sp)
                        Spacer(Modifier.width(12.dp))
                        Text(c.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Text("+${c.dial}", color = AreadColors.Muted)
                    }
                }
            }
        }
    }
}

@Composable
fun ChoiceChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier
            .clip(shape)
            .border(1.5.dp, if (selected) AreadColors.Accent else AreadColors.Line, shape)
            .background(if (selected) AreadColors.AccentSoft else AreadColors.Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (selected) Text("✓", color = AreadColors.Accent, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun Pill(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .clip(shape)
            .background(if (selected) AreadColors.Accent else AreadColors.AccentSoft)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else AreadColors.Accent,
            style = MaterialTheme.typography.labelLarge,
        )
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

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier.padding(top = 20.dp, bottom = 8.dp),
        style = MaterialTheme.typography.labelMedium,
        color = AreadColors.Muted,
        letterSpacing = 1.sp,
    )
}

@Composable
fun AreadCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    val base = modifier
        .fillMaxWidth()
        .clip(shape)
        .border(1.dp, AreadColors.Line, shape)
        .background(AreadColors.Surface)
    Column((if (onClick != null) base.clickable(onClick = onClick) else base).padding(18.dp), content = content)
}

@Composable
fun IconTile(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = AreadColors.Accent,
    bg: Color = AreadColors.AccentSoft,
    size: Dp = 44.dp,
) {
    Box(modifier.size(size).clip(RoundedCornerShape(14.dp)).background(bg), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    stroke: Dp = 14.dp,
    track: Color = AreadColors.AccentSoft,
    color: Color = AreadColors.Accent,
    content: @Composable BoxScope.() -> Unit,
) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(700), label = "ring")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = stroke.toPx()
            val topLeft = Offset(s / 2, s / 2)
            val arcSize = Size(size.width - s, size.height - s)
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(s))
            if (animated > 0.001f) {
                drawArc(color, -90f, 360f * animated, false, topLeft, arcSize, style = Stroke(s, cap = StrokeCap.Round))
            }
        }
        content()
    }
}

@Composable
fun Avatar(name: String, size: Dp, modifier: Modifier = Modifier) {
    val path = ThemeState.avatarPath
    Box(
        modifier.size(size).clip(CircleShape).background(AreadColors.Accent),
        contentAlignment = Alignment.Center,
    ) {
        if (path != null && File(path).exists()) {
            AsyncImage(model = File(path), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(
                name.trim().take(1).uppercase().ifEmpty { "A" },
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.titleLarge,
            )
        }
    }
}

@Composable
fun BookGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val left = Path().apply {
            moveTo(w * 0.47f, h * 0.40f)
            cubicTo(w * 0.36f, h * 0.31f, w * 0.20f, h * 0.31f, w * 0.08f, h * 0.36f)
            lineTo(w * 0.08f, h * 0.78f)
            cubicTo(w * 0.20f, h * 0.73f, w * 0.36f, h * 0.73f, w * 0.47f, h * 0.83f)
            close()
        }
        val right = Path().apply {
            moveTo(w * 0.53f, h * 0.40f)
            cubicTo(w * 0.64f, h * 0.31f, w * 0.80f, h * 0.31f, w * 0.92f, h * 0.36f)
            lineTo(w * 0.92f, h * 0.78f)
            cubicTo(w * 0.80f, h * 0.73f, w * 0.64f, h * 0.73f, w * 0.53f, h * 0.83f)
            close()
        }
        val cx = w * 0.5f
        val cy = h * 0.17f
        val r = w * 0.1f
        val spark = Path().apply {
            moveTo(cx, cy - r)
            quadraticBezierTo(cx, cy, cx + r, cy)
            quadraticBezierTo(cx, cy, cx, cy + r)
            quadraticBezierTo(cx, cy, cx - r, cy)
            quadraticBezierTo(cx, cy, cx, cy - r)
            close()
        }
        drawPath(left, color)
        drawPath(right, color)
        drawPath(spark, color)
    }
}

@Composable
fun MedhaFace(mouth: Mouth, modifier: Modifier = Modifier) {
    val inf = rememberInfiniteTransition(label = "medha")
    val blink by inf.animateFloat(
        0f, 1f,
        infiniteRepeatable(keyframes {
            durationMillis = 3800
            0f at 0
            0f at 3500
            1f at 3600
            0f at 3700
        }),
        label = "blink",
    )
    val bob by inf.animateFloat(-1f, 1f, infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob")
    val jaw by animateFloatAsState(mouth.jawOpen, tween(80), label = "jaw")
    val funnel by animateFloatAsState(mouth.funnel, tween(80), label = "funnel")
    val smile by animateFloatAsState(mouth.smile, tween(80), label = "smile")
    val p = LocalPalette.current

    Canvas(modifier.aspectRatio(1f)) {
        val w = size.width
        val h = size.height
        val cx = w / 2
        val cy = h / 2 + bob * h * 0.006f
        val skin = Color(0xFFE9B88F)
        val hair = Color(0xFF2A1E1A)
        val circle = Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset(w * 0.02f, h * 0.02f), Size(w * 0.96f, h * 0.96f))) }
        drawCircle(p.accentSoft, radius = w * 0.48f, center = Offset(cx, h / 2))
        clipPath(circle) {
            drawRoundRect(p.accent, Offset(cx - w * 0.3f, cy + h * 0.27f), Size(w * 0.6f, h * 0.4f), CornerRadius(w * 0.22f))
            drawRoundRect(skin, Offset(cx - w * 0.06f, cy + h * 0.17f), Size(w * 0.12f, h * 0.14f), CornerRadius(w * 0.04f))
            drawOval(hair, Offset(cx - w * 0.27f, cy - h * 0.3f), Size(w * 0.54f, h * 0.56f))
            drawCircle(skin, radius = w * 0.225f, center = Offset(cx, cy - h * 0.02f))
            drawArc(hair, 180f, 180f, true, Offset(cx - w * 0.245f, cy - h * 0.27f), Size(w * 0.49f, h * 0.36f))
            drawOval(hair, Offset(cx - w * 0.245f, cy - h * 0.16f), Size(w * 0.16f, h * 0.1f))
            drawCircle(Color(0xFFE08B7A).copy(alpha = 0.35f), w * 0.034f, Offset(cx - w * 0.13f, cy + h * 0.045f))
            drawCircle(Color(0xFFE08B7A).copy(alpha = 0.35f), w * 0.034f, Offset(cx + w * 0.13f, cy + h * 0.045f))
            val eyeH = h * 0.034f * (1f - blink * 0.92f)
            val eyeW = w * 0.024f
            listOf(-1f, 1f).forEach { side ->
                val ex = cx + side * w * 0.085f
                drawOval(Color(0xFF241A17), Offset(ex - eyeW, cy - h * 0.035f - eyeH), Size(eyeW * 2, eyeH * 2))
                drawArc(
                    hair, 200f, 140f, false, Offset(ex - w * 0.04f, cy - h * 0.095f), Size(w * 0.08f, h * 0.05f),
                    style = Stroke(w * 0.008f, cap = StrokeCap.Round),
                )
            }
            val my = cy + h * 0.075f
            val openH = h * (0.008f + jaw * 0.07f)
            if (jaw < 0.06f) {
                val mw = w * (0.075f + smile * 0.03f)
                drawArc(
                    Color(0xFF8A3F3A), 20f, 140f, false, Offset(cx - mw, my - mw * 0.55f), Size(mw * 2, mw * 1.1f),
                    style = Stroke(w * 0.009f, cap = StrokeCap.Round),
                )
            } else {
                val mw = w * (0.04f + (1f - funnel * 0.4f) * 0.025f + smile * 0.025f)
                drawOval(Color(0xFF7A2E2E), Offset(cx - mw, my - openH / 2), Size(mw * 2, openH))
            }
        }
    }
}
