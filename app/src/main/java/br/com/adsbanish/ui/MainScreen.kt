package br.com.adsbanish.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalConfiguration
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import br.com.adsbanish.BuildConfig
import br.com.adsbanish.R
import br.com.adsbanish.blocklist.BlocklistSource
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
    val state             by vpnState.collectAsState()
    val downloadState     by viewModel.downloadState.collectAsState()
    val downloadStatus    by viewModel.downloadStatus.collectAsState()
    val domainCount       by viewModel.domainCount.collectAsState()
    val lastUpdate        by viewModel.lastUpdate.collectAsState()
    val uptime            by viewModel.uptime.collectAsState()
    val autoStartHint     by viewModel.autoStartHint.collectAsState()
    val sourcesEnabled    by viewModel.sourcesEnabled.collectAsState()
    val sourceDomainCounts by viewModel.sourceDomainCounts.collectAsState()

    val isActive      = state is VpnState.Active
    val isDownloading = downloadState is DownloadState.Loading

    var showHelpDialog     by remember { mutableStateOf(false) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var showSourcesDialog  by remember { mutableStateOf(false) }
    var firstActivation    by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(downloadState) {
        if (downloadState is DownloadState.Loading) showDownloadDialog = true
        if (firstActivation) {
            when (downloadState) {
                is DownloadState.Success -> {
                    firstActivation    = false
                    showDownloadDialog = false
                    viewModel.dismissDownload()
                    onVpnToggle(state)
                }
                is DownloadState.Error -> firstActivation = false
                else -> Unit
            }
        }
    }

    val inf = rememberInfiniteTransition(label = "anim")

    val scanFraction by inf.animateFloat(
        initialValue = -0.12f,
        targetValue  = 1.0f,
        animationSpec = infiniteRepeatable(
            animation  = tween(2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "scan"
    )

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

    // ── Dialogs ───────────────────────────────────────────────────────────────
    if (showHelpDialog) {
        HelpDialog(onDismiss = { showHelpDialog = false })
    }

    if (showSourcesDialog) {
        SourcesDialog(
            sourcesEnabled     = sourcesEnabled,
            sourceDomainCounts = sourceDomainCounts,
            isEditable         = viewModel.hasDownloadedList,
            onToggle           = { source, enabled -> viewModel.toggleSource(source, enabled) },
            onDismiss          = {
                showSourcesDialog = false
                viewModel.refreshDomainCount()
            }
        )
    }

    if (showDownloadDialog) {
        DownloadDialog(
            downloadState   = downloadState,
            downloadStatus  = downloadStatus,
            domainCount     = domainCount,
            firstActivation = firstActivation,
            onDismiss       = {
                showDownloadDialog = false
                viewModel.dismissDownload()
            }
        )
    }

    // ── Layout ────────────────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .drawBehind {
                val step = 24.dp.toPx()
                val lineClr = if (isActive) Color(0x0800FF66) else Color(0x06FFFFFF)
                var y = 0f; while (y <= size.height) {
                    drawLine(lineClr, Offset(0f, y), Offset(size.width, y), 1f); y += step
                }
                var x = 0f; while (x <= size.width) {
                    drawLine(lineClr, Offset(x, 0f), Offset(x, size.height), 1f); x += step
                }
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 24.dp)
                .padding(top = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BrandHeader(
                isActive    = isActive,
                onHelpClick = { showHelpDialog = true },
                onMenuClick = { showSourcesDialog = true }
            )
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

            Spacer(modifier = Modifier.weight(1f))

            MainToggleButton(
                isActive = isActive,
                onClick  = {
                    if (!isActive && !viewModel.hasDownloadedList) {
                        firstActivation = true
                        viewModel.updateBlocklist()
                        showDownloadDialog = true
                    } else {
                        onVpnToggle(state)
                    }
                }
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
                isDownloading = isDownloading,
                onClick = {
                    viewModel.updateBlocklist()
                    showDownloadDialog = true
                }
            )

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

// ── BrandHeader ───────────────────────────────────────────────────────────────
@Composable
private fun BrandHeader(isActive: Boolean, onHelpClick: () -> Unit, onMenuClick: () -> Unit) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
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
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text     = "≡",
                fontFamily    = JBMono,
                fontWeight    = FontWeight.Bold,
                fontSize      = 13.sp,
                color         = TxtDim,
                modifier      = Modifier
                    .clickable(onClick = onMenuClick)
                    .border(1.dp, TxtDim)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
            Text(
                text          = "?",
                fontFamily    = JBMono,
                fontWeight    = FontWeight.Bold,
                fontSize      = 13.sp,
                color         = TxtDim,
                modifier      = Modifier
                    .clickable(onClick = onHelpClick)
                    .border(1.dp, TxtDim)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
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
        Box(
            modifier = Modifier
                .offset(6.dp, 6.dp)
                .size(220.dp)
                .background(shadowColor)
        )
        Box(
            modifier = Modifier
                .size(220.dp)
                .background(Surface)
                .border(2.5.dp, strokeColor)
        ) {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                val gridStep = 22.dp.toPx()
                val gridClr = if (isActive) Color(0x0C00FF66) else Color(0x0CFFFFFF)
                var gy = 0f; while (gy <= h) {
                    drawLine(gridClr, Offset(0f, gy), Offset(w, gy), 1f); gy += gridStep
                }
                var gx = 0f; while (gx <= w) {
                    drawLine(gridClr, Offset(gx, 0f), Offset(gx, h), 1f); gx += gridStep
                }

                val slStep = 4.dp.toPx()
                val slClr  = Color(0x0AFFFFFF)
                var sy = 0f; while (sy <= h) {
                    drawLine(slClr, Offset(0f, sy), Offset(w, sy), 1f); sy += slStep
                }

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

                val scale = h * 0.75f / 140f
                val ox    = (w - 120f * scale) / 2f
                val oy    = (h - 140f * scale) / 2f

                val shield = Path().apply {
                    moveTo(ox + 60*scale, oy + 6*scale)
                    lineTo(ox + 108*scale, oy + 24*scale)
                    lineTo(ox + 108*scale, oy + 70*scale)
                    quadraticTo(ox + 108*scale, oy + 108*scale, ox + 60*scale, oy + 134*scale)
                    quadraticTo(ox + 12*scale, oy + 108*scale, ox + 12*scale, oy + 70*scale)
                    lineTo(ox + 12*scale, oy + 24*scale)
                    close()
                }
                drawPath(
                    path  = shield,
                    color = strokeColor,
                    style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

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

            Row(
                modifier          = Modifier.align(Alignment.TopStart).padding(start = 14.dp, top = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text       = if (isActive) "STATUS:OK" else "STATUS:--",
                    fontFamily = JBMono,
                    fontWeight = FontWeight.Medium,
                    fontSize   = 8.sp,
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

            Text(
                text       = "v${BuildConfig.VERSION_NAME}",
                fontFamily = JBMono,
                fontWeight = FontWeight.Bold,
                fontSize   = 8.sp,
                color      = TxtDim,
                modifier   = Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 7.dp)
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
                text          = "DOMÍNIOS ATIVOS",
                fontFamily    = JBMono,
                fontWeight    = FontWeight.Normal,
                fontSize      = 9.sp,
                letterSpacing = 0.5.sp,
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Surface)
                .border(1.5.dp, Amber)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text          = "! AUTO-START BLOQUEADO",
                    fontFamily    = JBMono,
                    fontWeight    = FontWeight.ExtraBold,
                    fontSize      = 10.sp,
                    letterSpacing = 1.5.sp,
                    color         = Amber
                )
                Text(
                    text       = "Config. → Apps → ADSBanish → Bateria → Sem restrições",
                    fontFamily = JBMono,
                    fontWeight = FontWeight.Normal,
                    fontSize   = 9.sp,
                    color      = TxtMid
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick  = onOpenSettings,
                    modifier = Modifier.height(32.dp),
                    shape    = RectangleShape,
                    border   = BorderStroke(1.5.dp, Amber),
                    colors   = ButtonDefaults.outlinedButtonColors(containerColor = Bg, contentColor = Amber),
                    contentPadding = PaddingValues(horizontal = 10.dp)
                ) {
                    Text("CONFIG", fontFamily = JBMono, fontWeight = FontWeight.Bold, fontSize = 9.sp)
                }
                OutlinedButton(
                    onClick  = onDismiss,
                    modifier = Modifier.height(32.dp),
                    shape    = RectangleShape,
                    border   = BorderStroke(1.5.dp, TxtDim),
                    colors   = ButtonDefaults.outlinedButtonColors(containerColor = Bg, contentColor = TxtDim),
                    contentPadding = PaddingValues(horizontal = 10.dp)
                ) {
                    Text("OK", fontFamily = JBMono, fontWeight = FontWeight.Bold, fontSize = 9.sp)
                }
            }
        }
    }
}

// ── UpdateButton ──────────────────────────────────────────────────────────────
@Composable
private fun UpdateButton(isDownloading: Boolean, onClick: () -> Unit) {
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
            CircularProgressIndicator(modifier = Modifier.size(13.dp), color = TxtDim, strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                text          = "BAIXANDO…",
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
}

// ── DownloadDialog ────────────────────────────────────────────────────────────
@Composable
private fun DownloadDialog(
    downloadState   : DownloadState,
    downloadStatus  : String,
    domainCount     : Int,
    firstActivation : Boolean = false,
    onDismiss       : () -> Unit
) {
    val isLoading = downloadState is DownloadState.Loading
    val isError   = downloadState is DownloadState.Error
    val isSuccess = downloadState is DownloadState.Success
    val accent    = when { isError -> Red; isSuccess -> Green; else -> TxtMid }

    Dialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        properties = DialogProperties(dismissOnClickOutside = false, dismissOnBackPress = !isLoading)
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            Box(
                modifier = Modifier
                    .offset(4.dp, 4.dp)
                    .fillMaxWidth()
                    .background(accent.copy(alpha = if (isLoading) 0.3f else 1f))
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Surface)
                    .border(2.dp, accent)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = when {
                        isError   -> "ERRO NA ATUALIZAÇÃO"
                        isSuccess -> "LISTA ATUALIZADA"
                        else      -> "BAIXANDO LISTA"
                    },
                    fontFamily    = JBMono,
                    fontWeight    = FontWeight.ExtraBold,
                    fontSize      = 13.sp,
                    letterSpacing = 2.sp,
                    color         = accent
                )

                if (firstActivation && isLoading) {
                    Text(
                        text       = "Antes de ativar é necessário baixar a lista",
                        fontFamily = JBMono,
                        fontWeight = FontWeight.Normal,
                        fontSize   = 11.sp,
                        color      = TxtMid
                    )
                }

                when {
                    isLoading -> {
                        val progress = (downloadState as DownloadState.Loading).progress
                        LinearProgressIndicator(
                            progress      = { progress / 100f },
                            modifier      = Modifier.fillMaxWidth().height(2.dp),
                            color         = Green,
                            trackColor    = BorderHi,
                            strokeCap     = StrokeCap.Square
                        )
                        Text(
                            text       = "$progress%",
                            fontFamily = JBMono,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 22.sp,
                            color      = TxtHi
                        )
                        if (downloadStatus.isNotEmpty()) {
                            Text(
                                text       = downloadStatus,
                                fontFamily = JBMono,
                                fontSize   = 9.sp,
                                color      = TxtDim
                            )
                        }
                    }
                    isError -> {
                        Text(
                            text       = (downloadState as DownloadState.Error).message,
                            fontFamily = JBMono,
                            fontSize   = 11.sp,
                            color      = TxtMid
                        )
                    }
                    isSuccess -> {
                        val fmt = NumberFormat.getNumberInstance(Locale("pt", "BR"))
                        Text(
                            text       = "${fmt.format(domainCount)} domínios carregados",
                            fontFamily = JBMono,
                            fontWeight = FontWeight.Medium,
                            fontSize   = 14.sp,
                            color      = TxtHi
                        )
                    }
                }

                OutlinedButton(
                    onClick  = onDismiss,
                    enabled  = !isLoading,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape    = RectangleShape,
                    border   = BorderStroke(2.dp, if (isLoading) TxtDim else accent),
                    colors   = ButtonDefaults.outlinedButtonColors(
                        containerColor         = Bg,
                        contentColor           = accent,
                        disabledContainerColor = Bg,
                        disabledContentColor   = TxtDim
                    )
                ) {
                    Text(
                        text          = "OK",
                        fontFamily    = JBMono,
                        fontWeight    = FontWeight.ExtraBold,
                        fontSize      = 14.sp,
                        letterSpacing = 3.sp
                    )
                }
            }
        }
    }
}

// ── SourcesDialog ─────────────────────────────────────────────────────────────
@Composable
private fun SourcesDialog(
    sourcesEnabled     : Map<BlocklistSource, Boolean>,
    sourceDomainCounts : Map<BlocklistSource, Int>,
    isEditable         : Boolean,
    onToggle           : (BlocklistSource, Boolean) -> Unit,
    onDismiss          : () -> Unit
) {
    val maxH = (LocalConfiguration.current.screenHeightDp * 0.88f).dp
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = true)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxH)
                .padding(horizontal = 8.dp)
        ) {
            Box(Modifier.offset(4.dp, 4.dp).matchParentSize().background(Green))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Surface)
                    .border(2.dp, Green)
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                Text(
                    text          = "FONTES DE BLOQUEIO",
                    fontFamily    = JBMono,
                    fontWeight    = FontWeight.ExtraBold,
                    fontSize      = 13.sp,
                    letterSpacing = 2.sp,
                    color         = Green
                )
                Spacer(Modifier.height(12.dp))

                if (!isEditable) {
                    Text(
                        text          = "Baixe a lista antes de alterar as fontes",
                        fontFamily    = JBMono,
                        fontWeight    = FontWeight.Normal,
                        fontSize      = 9.sp,
                        color         = TxtDim,
                        modifier      = Modifier.padding(bottom = 4.dp)
                    )
                }

                BlocklistSource.entries.forEachIndexed { index, source ->
                    if (index > 0) {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(Border))
                    }
                    SourceRow(
                        source      = source,
                        enabled     = sourcesEnabled[source] ?: true,
                        isEditable  = isEditable,
                        domainCount = sourceDomainCounts[source] ?: 0,
                        onToggle    = { onToggle(source, it) }
                    )
                }

                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick  = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape    = RectangleShape,
                    border   = BorderStroke(2.dp, Green),
                    colors   = ButtonDefaults.outlinedButtonColors(
                        containerColor = Bg,
                        contentColor   = Green
                    )
                ) {
                    Text(
                        text          = "OK",
                        fontFamily    = JBMono,
                        fontWeight    = FontWeight.ExtraBold,
                        fontSize      = 13.sp,
                        letterSpacing = 3.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceRow(
    source      : BlocklistSource,
    enabled     : Boolean,
    isEditable  : Boolean,
    domainCount : Int,
    onToggle    : (Boolean) -> Unit
) {
    val nameColor   = when { !isEditable -> TxtDim; enabled -> TxtHi; else -> TxtDim }
    val toggleColor = when { !isEditable -> TxtDim; enabled -> Green; else -> TxtDim }

    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .then(if (isEditable) Modifier.clickable { onToggle(!enabled) } else Modifier)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text       = source.displayName,
                fontFamily = JBMono,
                fontWeight = FontWeight.SemiBold,
                fontSize   = 11.sp,
                color      = nameColor
            )
            Text(
                text       = source.description,
                fontFamily = JBMono,
                fontWeight = FontWeight.Normal,
                fontSize   = 9.sp,
                color      = TxtDim
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text          = if (enabled) "[ON ]" else "[OFF]",
            fontFamily    = JBMono,
            fontWeight    = FontWeight.Bold,
            fontSize      = 10.sp,
            letterSpacing = 1.sp,
            color         = toggleColor
        )
    }
}

// ── HelpDialog ────────────────────────────────────────────────────────────────
@Composable
private fun HelpDialog(onDismiss: () -> Unit) {
    val maxH = (LocalConfiguration.current.screenHeightDp * 0.88f).dp
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = true)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxH)
                .padding(horizontal = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .offset(4.dp, 4.dp)
                    .matchParentSize()
                    .background(Green)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Surface)
                    .border(2.dp, Green)
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text          = "COMO USAR",
                    fontFamily    = JBMono,
                    fontWeight    = FontWeight.ExtraBold,
                    fontSize      = 13.sp,
                    letterSpacing = 2.sp,
                    color         = Green
                )

                HelpSection(
                    title = "[ ATIVAR / DESATIVAR ]",
                    body  = "Toque no botão principal para ligar ou desligar o bloqueio. Na primeira ativação o app baixa as listas automaticamente. O bloqueio usa uma VPN local — nenhum dado sai do dispositivo."
                )
                HelpSection(
                    title = "[ FONTES DE BLOQUEIO ]",
                    body  = "Toque em [≡] para ver e ativar/desativar cada lista individualmente. Útil para liberar domínios bloqueados por listas mais agressivas sem desativar tudo."
                )
                HelpSection(
                    title = "[ ATUALIZAR LISTA ]",
                    body  = "Baixa as versões mais recentes de todas as listas ativas. Recomendado ao instalar e mensalmente para manter a proteção atualizada."
                )
                HelpSection(
                    title = "[ AUTO-START ]",
                    body  = "Para ativar automaticamente após reiniciar:\nConfig. → Apps → ADSBanish → Bateria → Sem restrições (ou Irrestrito)"
                )
                HelpSection(
                    title = "[ COMO FUNCIONA ]",
                    body  = "Intercepta consultas DNS e bloqueia domínios de anúncios, rastreadores e malware. Sites e serviços legítimos (Google, WhatsApp, bancos…) nunca são bloqueados."
                )

                OutlinedButton(
                    onClick  = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape    = RectangleShape,
                    border   = BorderStroke(2.dp, Green),
                    colors   = ButtonDefaults.outlinedButtonColors(containerColor = Bg, contentColor = Green)
                ) {
                    Text(
                        text          = "OK",
                        fontFamily    = JBMono,
                        fontWeight    = FontWeight.ExtraBold,
                        fontSize      = 14.sp,
                        letterSpacing = 3.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun HelpSection(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text          = title,
            fontFamily    = JBMono,
            fontWeight    = FontWeight.SemiBold,
            fontSize      = 10.sp,
            letterSpacing = 1.sp,
            color         = TxtHi
        )
        Text(
            text       = body,
            fontFamily = JBMono,
            fontWeight = FontWeight.Normal,
            fontSize   = 10.sp,
            color      = TxtMid
        )
    }
}
