package br.com.adsbanish.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import br.com.adsbanish.BuildConfig
import br.com.adsbanish.R
import br.com.adsbanish.vpn.VpnState
import kotlinx.coroutines.flow.StateFlow
import java.text.NumberFormat
import java.util.Locale

// ── Tokens ───────────────────────────────────────────────────────────────────
private val Bg       = Color(0xFF000000)
private val Surface  = Color(0xFF0A0A0A)
private val Border   = Color(0xFF1F1F1F)
private val BorderHi = Color(0xFF2A2A2A)
private val TxtHi    = Color(0xFFF0F0F0)
private val TxtMid   = Color(0xFF9A9A9A)
private val TxtDim   = Color(0xFF6B6B6B)
private val Green    = Color(0xFF00FF66)
private val Red      = Color(0xFFFF3355)

// ── Font ─────────────────────────────────────────────────────────────────────
private val JBMono = FontFamily(
    Font(R.font.jetbrains_mono_regular,   FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium,    FontWeight.Medium),
    Font(R.font.jetbrains_mono_semibold,  FontWeight.SemiBold),
    Font(R.font.jetbrains_mono_bold,      FontWeight.Bold),
    Font(R.font.jetbrains_mono_extrabold, FontWeight.ExtraBold),
)

// ── Screen ───────────────────────────────────────────────────────────────────
@Composable
fun MainScreen(
    vpnState    : StateFlow<VpnState>,
    viewModel   : MainViewModel,
    onVpnToggle : (VpnState) -> Unit
) {
    val state          by vpnState.collectAsState()
    val downloadState  by viewModel.downloadState.collectAsState()
    val downloadStatus by viewModel.downloadStatus.collectAsState()
    val domainCount    by viewModel.domainCount.collectAsState()
    val lastUpdate     by viewModel.lastUpdate.collectAsState()
    val uptime         by viewModel.uptime.collectAsState()
    val autoStartHint  by viewModel.autoStartHint.collectAsState()

    val isActive      = state is VpnState.Active
    val isDownloading = downloadState is DownloadState.Loading

    val inf = rememberInfiniteTransition(label = "anim")

    // Scan line: fraction -0.12..1.0 across shield height
    val scanFraction by inf.animateFloat(
        initialValue = -0.12f,
        targetValue  = 1.0f,
        animationSpec = infiniteRepeatable(
            animation  = tween(2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "scan"
    )

    // Cursor blink: steps(1) at 1s
    val cursorAlpha by inf.animateFloat(
        initialValue = 0f,
        targetValue  = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1000
                1f at 0   using LinearEasing
                1f at 499 using LinearEasing
                0f at 500 using LinearEasing
                0f at 999 using LinearEasing
            }
        ), label = "cursor"
    )

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(downloadState) {
        if (downloadState is DownloadState.Error) {
            snackbarHostState.showSnackbar(
                message     = (downloadState as DownloadState.Error).message,
                actionLabel = "OK"
            )
            viewModel.dismissError()
        }
    }

    Scaffold(
        snackbarHost   = { SnackbarHost(snackbarHostState) },
        containerColor = Bg
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Bg)
                .drawBehind {
                    // Background grid 24×24px
                    val step = 24.dp.toPx()
                    val lineClr = if (isActive) Color(0x0800FF66) else Color(0x06FFFFFF)
                    var y = 0f; while (y <= size.height) {
                        drawLine(lineClr, Offset(0f, y), Offset(size.width, y), 1f); y += step
                    }
                    var x = 0f; while (x <= size.width) {
                        drawLine(lineClr, Offset(x, 0f), Offset(x, size.height), 1f); x += step
                    }
                    // Radial mask: grid fades from center-top toward edges
                    drawRect(
                        brush = Brush.radialGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Transparent,
                                0.55f to Color.Transparent,
                                1.0f to Color.Black
                            ),
                            center = Offset(size.width / 2f, 0f),
                            radius = maxOf(size.width, size.height) * 1.1f
                        )
                    )
                }
        ) {
            // Single column — weight(1f) spacer empurra os botões para baixo
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
                    .padding(top = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ── Top content ──────────────────────────────────────────────
                BrandHeader(isActive = isActive)
                Spacer(Modifier.height(32.dp))
                ShieldArt(
                    isActive     = isActive,
                    scanFraction = if (isActive) scanFraction else -1f,
                    cursorAlpha  = if (isActive) cursorAlpha else 0f
                )
                Spacer(Modifier.height(28.dp))
                StatusPill(isActive = isActive)
                Spacer(Modifier.height(20.dp))
                MetaLine(domainCount = domainCount, uptime = uptime, isActive = isActive)

                // ── Spacer empurra para ~20% acima da base ───────────────────
                Spacer(modifier = Modifier.weight(1f))

                // ── Buttons ──────────────────────────────────────────────────
                MainToggleButton(
                    isActive = isActive,
                    onClick  = { onVpnToggle(state) }
                )
                Spacer(Modifier.height(12.dp))
                if (autoStartHint) {
                    val ctx = LocalContext.current
                    AutoStartWarningCard(
                        onOpenSettings = {
                            ctx.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.parse("package:${ctx.packageName}")
                                }
                            )
                        },
                        onDismiss = { viewModel.dismissAutoStartHint() }
                    )
                    Spacer(Modifier.height(12.dp))
                }
                UpdateButton(
                    isDownloading  = isDownloading,
                    downloadState  = downloadState,
                    downloadStatus = downloadStatus,
                    onClick        = { viewModel.updateBlocklist() }
                )

                // ── Footer ───────────────────────────────────────────────────
                Spacer(Modifier.height(20.dp))
                Text(
                    text          = "ÚLT. ATT $lastUpdate",
                    fontFamily    = JBMono,
                    fontWeight    = FontWeight.Normal,
                    fontSize      = 9.sp,
                    letterSpacing = 1.sp,
                    color         = TxtDim,
                    textAlign     = TextAlign.Center,
                    modifier      = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

// ── BrandHeader ───────────────────────────────────────────────────────────────
@Composable
private fun BrandHeader(isActive: Boolean) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        // Brand mark: "ads/banish" + colored "_"
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = TxtHi)) { append("ads/banish") }
                withStyle(SpanStyle(color = if (isActive) Green else TxtHi)) { append("_") }
            },
            fontFamily    = JBMono,
            fontWeight    = FontWeight.Bold,
            fontSize      = 14.sp,
            letterSpacing = 2.sp
        )
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text          = if (isActive) "live" else "idle",
                fontFamily    = JBMono,
                fontWeight    = FontWeight.Medium,
                fontSize      = 11.sp,
                letterSpacing = 2.sp,
                color         = if (isActive) Green else TxtDim
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .then(
                        if (isActive) Modifier.background(Green)
                        else Modifier.border(1.5.dp, TxtDim)
                    )
            )
        }
    }
}

// ── ShieldArt ────────────────────────────────────────────────────────────────
@Composable
private fun ShieldArt(isActive: Boolean, scanFraction: Float, cursorAlpha: Float) {
    val strokeColor = if (isActive) Green else TxtDim
    val shadowColor = if (isActive) Green else Color(0xFF171717)

    Box(modifier = Modifier.size(226.dp)) {
        // Hard-offset shadow stamp (translate +6,+6)
        Box(
            modifier = Modifier
                .offset(6.dp, 6.dp)
                .size(220.dp)
                .background(shadowColor)
        )
        // Main block — background always #0A0A0A, no tint
        Box(
            modifier = Modifier
                .size(220.dp)
                .background(Surface)
                .border(2.5.dp, strokeColor)
        ) {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // Internal grid 22×22
                val gridStep = 22.dp.toPx()
                val gridClr = if (isActive) Color(0x0C00FF66) else Color(0x0CFFFFFF)
                var gy = 0f; while (gy <= h) {
                    drawLine(gridClr, Offset(0f, gy), Offset(w, gy), 1f); gy += gridStep
                }
                var gx = 0f; while (gx <= w) {
                    drawLine(gridClr, Offset(gx, 0f), Offset(gx, h), 1f); gx += gridStep
                }

                // Scanlines (horizontal, subtle, opacity 0.4)
                val slStep = 4.dp.toPx()
                val slClr  = Color(0x0AFFFFFF)
                var sy = 0f; while (sy <= h) {
                    drawLine(slClr, Offset(0f, sy), Offset(w, sy), 1f); sy += slStep
                }

                // Vertical scan sweep — active only, max opacity 0.5, blend=Screen
                if (scanFraction >= 0f) {
                    val scanH   = 26.dp.toPx()
                    val scanTop = scanFraction * h - scanH
                    clipRect(0f, 0f, w, h) {
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color(0x0000FF66),
                                    Color(0x2D00FF66),
                                    Color(0x8000FF66),
                                ),
                                startY = scanTop,
                                endY   = scanTop + scanH
                            ),
                            topLeft   = Offset(0f, scanTop),
                            size      = Size(w, scanH),
                            blendMode = BlendMode.Screen
                        )
                    }
                }

                // Shield path (coordinate space 120×140)
                val scale = h * 0.75f / 140f
                val ox    = (w - 120f * scale) / 2f
                val oy    = (h - 140f * scale) / 2f

                val shield = Path().apply {
                    moveTo(ox + 60*scale, oy + 6*scale)
                    lineTo(ox + 108*scale, oy + 24*scale)
                    lineTo(ox + 108*scale, oy + 70*scale)
                    quadraticBezierTo(ox + 108*scale, oy + 108*scale, ox + 60*scale, oy + 134*scale)
                    quadraticBezierTo(ox + 12*scale, oy + 108*scale, ox + 12*scale, oy + 70*scale)
                    lineTo(ox + 12*scale, oy + 24*scale)
                    close()
                }
                drawPath(
                    path  = shield,
                    color = strokeColor,
                    style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                // Interior icon — fill transparent in both states
                val iconStroke = Stroke(width = 6.5.dp.toPx(), cap = StrokeCap.Square)
                if (isActive) {
                    val check = Path().apply {
                        moveTo(ox + 38*scale, oy + 70*scale)
                        lineTo(ox + 54*scale, oy + 86*scale)
                        lineTo(ox + 84*scale, oy + 54*scale)
                    }
                    drawPath(check, Green, style = iconStroke)
                } else {
                    val xPath = Path().apply {
                        moveTo(ox + 44*scale, oy + 54*scale)
                        lineTo(ox + 76*scale, oy + 86*scale)
                        moveTo(ox + 76*scale, oy + 54*scale)
                        lineTo(ox + 44*scale, oy + 86*scale)
                    }
                    drawPath(xPath, TxtDim, style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Square))
                }

                // Corner brackets — 4× L-shape
                val ba  = 16.dp.toPx()
                val bw2 = 2.dp.toPx()
                val bi  = 8.dp.toPx()
                val bc  = strokeColor
                drawLine(bc, Offset(bi, bi),           Offset(bi + ba, bi),      bw2)
                drawLine(bc, Offset(bi, bi),           Offset(bi,      bi + ba), bw2)
                drawLine(bc, Offset(w-bi-ba, bi),      Offset(w-bi,    bi),      bw2)
                drawLine(bc, Offset(w-bi,    bi),      Offset(w-bi,    bi+ba),   bw2)
                drawLine(bc, Offset(bi,      h-bi-ba), Offset(bi,      h-bi),    bw2)
                drawLine(bc, Offset(bi,      h-bi),    Offset(bi+ba,   h-bi),    bw2)
                drawLine(bc, Offset(w-bi,    h-bi-ba), Offset(w-bi,    h-bi),    bw2)
                drawLine(bc, Offset(w-bi-ba, h-bi),    Offset(w-bi,    h-bi),    bw2)
            }

            // Top-left: STATUS:OK + blinking cursor (active) / STATUS:-- (inactive)
            Row(
                modifier          = Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text       = if (isActive) "STATUS:OK" else "STATUS:--",
                    fontFamily = JBMono,
                    fontWeight = FontWeight.Medium,
                    fontSize   = 7.sp,
                    color      = if (isActive) Green else TxtDim
                )
                if (isActive) {
                    Spacer(Modifier.width(2.dp))
                    Box(
                        modifier = Modifier
                            .size(4.dp, 8.dp)
                            .background(Green.copy(alpha = cursorAlpha))
                    )
                }
            }

            // Bottom-right: version
            Text(
                text       = "v${BuildConfig.VERSION_NAME}",
                fontFamily = JBMono,
                fontWeight = FontWeight.Normal,
                fontSize   = 7.sp,
                color      = TxtDim,
                modifier   = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 10.dp)
            )
        }
    }
}

// ── StatusPill ────────────────────────────────────────────────────────────────
@Composable
private fun StatusPill(isActive: Boolean) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .then(
                    if (isActive) Modifier.background(Green)
                    else Modifier.border(1.5.dp, TxtMid)
                )
        )
        Text(
            text          = if (isActive) "FILTRANDO TRÁFEGO" else "TRÁFEGO LIVRE",
            fontFamily    = JBMono,
            fontWeight    = FontWeight.ExtraBold,
            fontSize      = 18.sp,
            letterSpacing = 2.sp,
            color         = if (isActive) Green else TxtHi
        )
    }
}

// ── MetaLine ──────────────────────────────────────────────────────────────────
@Composable
private fun MetaLine(domainCount: Int, uptime: String, isActive: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface)
            .border(1.5.dp, Border)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = NumberFormat.getNumberInstance(Locale("pt", "BR")).format(domainCount),
                fontFamily = JBMono,
                fontWeight = FontWeight.Bold,
                fontSize   = 18.sp,
                color      = TxtHi
            )
            Text(
                text          = "DOMÍNIOS",
                fontFamily    = JBMono,
                fontWeight    = FontWeight.Normal,
                fontSize      = 9.sp,
                letterSpacing = 1.5.sp,
                color         = TxtDim
            )
        }

        Box(modifier = Modifier.width(1.5.dp).height(36.dp).background(BorderHi))

        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            Text(
                text       = uptime,
                fontFamily = JBMono,
                fontWeight = FontWeight.Bold,
                fontSize   = 18.sp,
                color      = if (isActive) Green else TxtMid
            )
            Text(
                text          = "UPTIME",
                fontFamily    = JBMono,
                fontWeight    = FontWeight.Normal,
                fontSize      = 9.sp,
                letterSpacing = 1.5.sp,
                color         = TxtDim
            )
        }
    }
}

// ── MainToggleButton ──────────────────────────────────────────────────────────
@Composable
private fun MainToggleButton(isActive: Boolean, onClick: () -> Unit) {
    val accent = if (isActive) Red else Green

    Box(modifier = Modifier.fillMaxWidth().height(77.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .offset(5.dp, 5.dp)
                .background(accent)
        )
        OutlinedButton(
            onClick  = onClick,
            modifier = Modifier.fillMaxWidth().height(72.dp),
            shape    = RectangleShape,
            border   = BorderStroke(2.5.dp, accent),
            colors   = ButtonDefaults.outlinedButtonColors(
                containerColor = Bg,
                contentColor   = accent
            )
        ) {
            if (isActive) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.width(4.dp).height(18.dp).background(accent))
                    Box(Modifier.width(4.dp).height(18.dp).background(accent))
                }
            } else {
                androidx.compose.foundation.Canvas(modifier = Modifier.size(18.dp)) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val r  = size.minDimension / 2f - 2.dp.toPx()
                    val sw = 2.5.dp.toPx()
                    drawArc(
                        color      = accent,
                        startAngle = -220f,
                        sweepAngle = 260f,
                        useCenter  = false,
                        style      = Stroke(sw, cap = StrokeCap.Round)
                    )
                    drawLine(accent, Offset(cx, 0f), Offset(cx, cy), sw)
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text          = if (isActive) "DESATIVAR" else "ATIVAR",
                fontFamily    = JBMono,
                fontWeight    = FontWeight.ExtraBold,
                fontSize      = 19.sp,
                letterSpacing = 4.sp,
                color         = accent
            )
        }
    }
}

// ── AutoStartWarningCard ──────────────────────────────────────────────────────
@Composable
private fun AutoStartWarningCard(onOpenSettings: () -> Unit, onDismiss: () -> Unit) {
    val Amber = Color(0xFFFFAA00)
    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset(4.dp, 4.dp)
                .background(Amber.copy(alpha = 0.25f))
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Surface)
                .border(1.5.dp, Amber)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text          = "! AUTO-START BLOQUEADO",
                fontFamily    = JBMono,
                fontWeight    = FontWeight.ExtraBold,
                fontSize      = 11.sp,
                letterSpacing = 2.sp,
                color         = Amber
            )
            Text(
                text       = "O VPN não foi reiniciado automaticamente no boot. O sistema bloqueou o início em segundo plano.",
                fontFamily = JBMono,
                fontWeight = FontWeight.Normal,
                fontSize   = 11.sp,
                color      = TxtMid
            )
            Text(
                text       = "Config. → Apps → ADSBanish → Bateria\n→ Sem restrições (ou Irrestrito)",
                fontFamily = JBMono,
                fontWeight = FontWeight.Medium,
                fontSize   = 10.sp,
                color      = TxtHi
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick  = onOpenSettings,
                    modifier = Modifier.weight(1f).height(40.dp),
                    shape    = RectangleShape,
                    border   = BorderStroke(1.5.dp, Amber),
                    colors   = ButtonDefaults.outlinedButtonColors(
                        containerColor = Bg, contentColor = Amber
                    )
                ) {
                    Text(
                        text          = "CONFIGURAR",
                        fontFamily    = JBMono,
                        fontWeight    = FontWeight.Bold,
                        fontSize      = 10.sp,
                        letterSpacing = 1.5.sp
                    )
                }
                OutlinedButton(
                    onClick  = onDismiss,
                    modifier = Modifier.weight(1f).height(40.dp),
                    shape    = RectangleShape,
                    border   = BorderStroke(1.5.dp, TxtDim),
                    colors   = ButtonDefaults.outlinedButtonColors(
                        containerColor = Bg, contentColor = TxtDim
                    )
                ) {
                    Text(
                        text          = "DISPENSAR",
                        fontFamily    = JBMono,
                        fontWeight    = FontWeight.Bold,
                        fontSize      = 10.sp,
                        letterSpacing = 1.5.sp
                    )
                }
            }
        }
    }
}

// ── UpdateButton ──────────────────────────────────────────────────────────────
@Composable
private fun UpdateButton(
    isDownloading  : Boolean,
    downloadState  : DownloadState,
    downloadStatus : String,
    onClick        : () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick  = onClick,
            enabled  = !isDownloading,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape    = RectangleShape,
            border   = BorderStroke(2.dp, if (isDownloading) TxtDim else Green),
            colors   = ButtonDefaults.outlinedButtonColors(
                containerColor         = Bg,
                contentColor           = Green,
                disabledContainerColor = Bg,
                disabledContentColor   = TxtDim
            )
        ) {
            if (isDownloading) {
                CircularProgressIndicator(
                    modifier    = Modifier.size(13.dp),
                    color       = TxtDim,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(10.dp))
                val progress = (downloadState as? DownloadState.Loading)?.progress ?: 0
                Text(
                    text          = "BAIXANDO… $progress%",
                    fontFamily    = JBMono,
                    fontWeight    = FontWeight.Bold,
                    fontSize      = 13.sp,
                    letterSpacing = 2.5.sp,
                    color         = TxtDim
                )
            } else {
                androidx.compose.foundation.Canvas(modifier = Modifier.size(14.dp)) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val r  = size.minDimension / 2f - 1.dp.toPx()
                    drawArc(
                        color      = Green,
                        startAngle = -30f,
                        sweepAngle = 300f,
                        useCenter  = false,
                        style      = Stroke(2.dp.toPx(), cap = StrokeCap.Round)
                    )
                    val tip = Path().apply {
                        moveTo(cx + r, cy - 3.dp.toPx())
                        lineTo(cx + r + 3.dp.toPx(), cy)
                        lineTo(cx + r, cy + 3.dp.toPx())
                    }
                    drawPath(tip, Green, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text          = "ATUALIZAR LISTA",
                    fontFamily    = JBMono,
                    fontWeight    = FontWeight.Bold,
                    fontSize      = 13.sp,
                    letterSpacing = 2.5.sp,
                    color         = Green
                )
            }
        }

        if (isDownloading) {
            val progress = (downloadState as? DownloadState.Loading)?.progress ?: 0
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress   = { progress / 100f },
                modifier   = Modifier.fillMaxWidth().height(2.dp),
                color      = Green,
                trackColor = BorderHi,
                strokeCap  = StrokeCap.Square
            )
            if (downloadStatus.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text       = downloadStatus,
                    fontFamily = JBMono,
                    fontSize   = 9.sp,
                    color      = TxtDim
                )
            }
        }
    }
}
