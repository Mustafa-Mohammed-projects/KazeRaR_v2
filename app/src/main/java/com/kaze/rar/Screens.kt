package com.kaze.rar

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

// =====================================================================
// Root
// =====================================================================

@Composable
fun KazeApp(vm: MainViewModel, onPick: () -> Unit, onOpenDownloads: () -> Unit) {
    var showSplash by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        if (showSplash) {
            delay(3000)          // presplash.png stays for exactly 3 seconds
            showSplash = false
        }
    }

    val state by vm.state.collectAsStateWithLifecycle()
    BackHandler(enabled = !showSplash && state !is UiState.Home) { vm.onBack() }

    Crossfade(targetState = showSplash, animationSpec = tween(600), label = "splash") { splash ->
        if (splash) {
            SplashScreen()
        } else {
            AppBackground {
                AnimatedContent(
                    targetState = state,
                    contentKey = { it::class },
                    transitionSpec = {
                        (fadeIn(tween(350)) + slideInVertically(tween(350)) { it / 14 }) togetherWith
                            fadeOut(tween(180))
                    },
                    label = "screen"
                ) { s ->
                    when (s) {
                        is UiState.Home -> HomeScreen(onPick)
                        is UiState.Loading -> LoadingScreen(s.message)
                        is UiState.Password -> PasswordScreen(s, vm::submitPassword, vm::reset)
                        is UiState.Browse -> BrowseScreen(s, vm::extract, vm::reset)
                        is UiState.Extracting -> ExtractingScreen(s, vm::onBack)
                        is UiState.Done -> DoneScreen(s, onOpenDownloads, vm::reset)
                        is UiState.Error -> ErrorScreen(s, vm::reset)
                    }
                }
            }
        }
    }
}

// =====================================================================
// Splash (presplash.png, 3 seconds)
// =====================================================================

@Composable
fun SplashScreen() {
    val fade = remember { Animatable(0f) }
    val zoom = remember { Animatable(0.92f) }
    LaunchedEffect(Unit) {
        launch { fade.animateTo(1f, tween(700)) }
        zoom.animateTo(1f, tween(1400, easing = FastOutSlowInEasing))
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(KazeColors.Bg),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.presplash),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = fade.value
                    scaleX = zoom.value
                    scaleY = zoom.value
                },
            contentScale = ContentScale.Fit
        )
    }
}

// =====================================================================
// Shared building blocks
// =====================================================================

@Composable
fun AppBackground(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(KazeColors.Bg)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(KazeColors.Primary.copy(alpha = 0.28f), Color.Transparent),
                        center = Offset(size.width * 0.85f, size.height * 0.08f),
                        radius = size.width * 0.9f
                    ),
                    radius = size.width * 0.9f,
                    center = Offset(size.width * 0.85f, size.height * 0.08f)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(KazeColors.Secondary.copy(alpha = 0.16f), Color.Transparent),
                        center = Offset(size.width * 0.05f, size.height * 0.95f),
                        radius = size.width * 0.9f
                    ),
                    radius = size.width * 0.9f,
                    center = Offset(size.width * 0.05f, size.height * 0.95f)
                )
            }
    ) { content() }
}

@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    brush: Brush = KazeBrush
) {
    Box(
        modifier
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(brush)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
    }
}

@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .border(1.dp, KazeColors.SurfaceHigh, RoundedCornerShape(26.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = KazeColors.TextHi, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
}

@Composable
private fun GradientCircle(size: Int, brush: Brush, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(brush),
        contentAlignment = Alignment.Center
    ) { content() }
}

fun formatSize(b: Long): String {
    if (b < 0) return "—"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var v = b.toDouble()
    var i = 0
    while (v >= 1024 && i < units.size - 1) { v /= 1024; i++ }
    return if (i == 0) "$b B" else String.format(Locale.US, "%.1f %s", v, units[i])
}

// =====================================================================
// Home
// =====================================================================

@Composable
fun HomeScreen(onPick: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(KazeBrush),
                contentAlignment = Alignment.Center
            ) { Text("K", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black) }
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(R.string.app_name),
                color = KazeColors.TextHi,
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.tagline),
            color = KazeColors.TextLo,
            fontSize = 15.sp,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.weight(1f))
        PickOrb(onPick)
        Spacer(Modifier.height(28.dp))
        GradientButton(
            stringResource(R.string.choose_archive),
            onPick,
            Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(26.dp))
        FormatChips()
        Spacer(Modifier.weight(1f))

        Text(
            stringResource(R.string.saved_hint),
            color = KazeColors.TextLo,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun PickOrb(onPick: () -> Unit) {
    val t = rememberInfiniteTransition(label = "orb")
    val pulse by t.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    val ring by t.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "ring"
    )
    Box(
        Modifier
            .size(230.dp)
            .clip(CircleShape)
            .clickable(onClick = onPick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(KazeColors.Primary.copy(alpha = ring * 0.55f), Color.Transparent)
                ),
                radius = r * pulse
            )
            drawCircle(
                brush = KazeBrush,
                radius = r * 0.78f * pulse,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                alpha = ring + 0.2f
            )
        }
        GradientCircle(120, KazeBrush) {
            Icon(
                Icons.Default.Add,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .size(60.dp)
                    .scale(pulse.coerceIn(0.95f, 1.05f))
            )
        }
    }
}

@Composable
private fun FormatChips() {
    val formats = listOf("ZIP", "RAR", "7Z", "TAR", "GZ", "BZ2", "XZ", "TGZ")
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        formats.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { f ->
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(KazeColors.Surface)
                            .border(1.dp, KazeColors.SurfaceHigh, RoundedCornerShape(50))
                            .padding(horizontal = 16.dp, vertical = 7.dp)
                    ) {
                        Text(f, color = KazeColors.TextLo, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

// =====================================================================
// Loading
// =====================================================================

@Composable
fun LoadingScreen(message: Int) {
    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(color = KazeColors.Secondary, trackColor = KazeColors.SurfaceHigh, strokeWidth = 5.dp)
        Spacer(Modifier.height(20.dp))
        Text(stringResource(message), color = KazeColors.TextLo, fontSize = 15.sp)
    }
}

// =====================================================================
// Password
// =====================================================================

@Composable
fun PasswordScreen(s: UiState.Password, onSubmit: (String) -> Unit, onCancel: () -> Unit) {
    var pw by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        GradientCircle(84, KazeBrush) {
            Icon(Icons.Default.Lock, null, tint = Color.White, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.password_title),
            color = KazeColors.TextHi, fontSize = 24.sp, fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.password_sub, s.name),
            color = KazeColors.TextLo, fontSize = 14.sp, textAlign = TextAlign.Center,
            maxLines = 2, overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = pw,
            onValueChange = { pw = it },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.password_hint)) },
            isError = s.wrong,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            shape = RoundedCornerShape(18.dp),
            trailingIcon = {
                TextButton(onClick = { visible = !visible }) {
                    Text(
                        stringResource(if (visible) R.string.hide else R.string.show),
                        color = KazeColors.Secondary
                    )
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = KazeColors.Primary,
                unfocusedBorderColor = KazeColors.SurfaceHigh,
                focusedTextColor = KazeColors.TextHi,
                unfocusedTextColor = KazeColors.TextHi,
                focusedLabelColor = KazeColors.Primary,
                unfocusedLabelColor = KazeColors.TextLo,
                cursorColor = KazeColors.Secondary
            )
        )
        if (s.wrong) {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.password_wrong), color = KazeColors.Danger, fontSize = 13.sp)
        }
        Spacer(Modifier.height(22.dp))
        GradientButton(stringResource(R.string.unlock), { if (pw.isNotEmpty()) onSubmit(pw) }, Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        TextButton(onClick = onCancel) {
            Text(stringResource(R.string.cancel), color = KazeColors.TextLo)
        }
    }
}

// =====================================================================
// Browse
// =====================================================================

private val badgeColors = listOf(
    Color(0xFF7C5CFF), Color(0xFF22D3EE), Color(0xFFFB7185),
    Color(0xFFF59E0B), Color(0xFF34D399), Color(0xFF60A5FA)
)

@Composable
fun BrowseScreen(s: UiState.Browse, onExtract: () -> Unit, onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 20.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = KazeColors.TextHi)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    s.name, color = KazeColors.TextHi, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    stringResource(R.string.files_count, s.fileCount) + "  ·  " +
                        stringResource(R.string.total_size, formatSize(s.totalBytes)),
                    color = KazeColors.TextLo, fontSize = 12.sp
                )
            }
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(KazeBrush)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) { Text(s.kind.label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold) }
        }

        Spacer(Modifier.height(8.dp))

        if (s.items.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.empty_archive), color = KazeColors.TextLo)
            }
        } else {
            LazyColumn(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(s.items, key = { i, _ -> i }) { _, item -> EntryRow(item) }
            }
        }

        GradientButton(
            text = stringResource(R.string.extract_all) + "  (${s.fileCount})",
            onClick = onExtract,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp)
        )
    }
}

@Composable
private fun EntryRow(item: Item) {
    val clean = item.path.trimEnd('/')
    val name = clean.substringAfterLast('/').ifEmpty { clean }
    val parent = clean.substringBeforeLast('/', "")
    val ext = if (item.isDir) "" else name.substringAfterLast('.', "").uppercase().take(4)
    val tint = badgeColors[(ext.hashCode() and 0x7fffffff) % badgeColors.size]

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(KazeColors.Surface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (item.isDir) Color(0x33F59E0B) else tint.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            if (item.isDir) {
                Text("📁", fontSize = 20.sp)
            } else {
                Text(
                    ext.ifEmpty { "FILE" },
                    color = tint, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                name, color = KazeColors.TextHi, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            if (parent.isNotEmpty()) {
                Text(
                    parent, color = KazeColors.TextLo, fontSize = 11.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (!item.isDir) {
            Spacer(Modifier.width(8.dp))
            Text(formatSize(item.size), color = KazeColors.TextLo, fontSize = 12.sp)
        }
    }
}

// =====================================================================
// Extracting
// =====================================================================

@Composable
fun ExtractingScreen(s: UiState.Extracting, onCancel: () -> Unit) {
    val animated by animateFloatAsState(s.fraction ?: 0f, tween(220), label = "progress")
    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(210.dp), contentAlignment = Alignment.Center) {
            if (s.fraction != null) {
                CircularProgressIndicator(
                    progress = { animated },
                    modifier = Modifier.fillMaxSize(),
                    color = KazeColors.Secondary,
                    trackColor = KazeColors.SurfaceHigh,
                    strokeWidth = 12.dp,
                    strokeCap = StrokeCap.Round
                )
                Text(
                    "${(animated * 100).toInt()}%",
                    color = KazeColors.TextHi, fontSize = 44.sp, fontWeight = FontWeight.ExtraBold
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.fillMaxSize(),
                    color = KazeColors.Secondary,
                    trackColor = KazeColors.SurfaceHigh,
                    strokeWidth = 12.dp,
                    strokeCap = StrokeCap.Round
                )
            }
        }
        Spacer(Modifier.height(28.dp))
        Text(
            stringResource(R.string.extracting),
            color = KazeColors.TextHi, fontSize = 22.sp, fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            s.current.ifEmpty { s.name },
            color = KazeColors.TextLo, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.progress_files, s.doneFiles, s.totalFiles),
            color = KazeColors.TextLo, fontSize = 12.sp
        )
        Spacer(Modifier.height(36.dp))
        GhostButton(stringResource(R.string.cancel), onCancel, Modifier.width(180.dp))
    }
}

// =====================================================================
// Done
// =====================================================================

@Composable
fun DoneScreen(s: UiState.Done, onOpenDownloads: () -> Unit, onNew: () -> Unit) {
    val pop = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }
    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value }) {
            GradientCircle(128, SuccessBrush) {
                Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(70.dp))
            }
        }
        Spacer(Modifier.height(28.dp))
        Text(
            stringResource(R.string.done_title),
            color = KazeColors.TextHi, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.done_sub, s.result.files, formatSize(s.result.bytes)),
            color = KazeColors.TextHi, fontSize = 16.sp
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.saved_in, s.result.folder),
            color = KazeColors.TextLo, fontSize = 12.sp, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(36.dp))
        GradientButton(stringResource(R.string.open_downloads), onOpenDownloads, Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        GhostButton(stringResource(R.string.new_archive), onNew, Modifier.fillMaxWidth())
    }
}

// =====================================================================
// Error
// =====================================================================

@Composable
fun ErrorScreen(s: UiState.Error, onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        GradientCircle(104, DangerBrush) {
            Icon(Icons.Default.Warning, null, tint = Color.White, modifier = Modifier.size(52.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.error_title),
            color = KazeColors.TextHi, fontSize = 24.sp, fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(s.message),
            color = KazeColors.TextLo, fontSize = 15.sp, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        GradientButton(stringResource(R.string.back), onBack, Modifier.fillMaxWidth())
    }
}
