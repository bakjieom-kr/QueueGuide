package com.example.queueguide

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.max

/**
 * Demo-only screen using a fixed screenshot of OpenStreetMap as a background.
 * The route and congestion overlay are hand-positioned for demonstration;
 * they are NOT real-road navigation data or real traffic observations.
 * MainActivity still owns the 72 km/h / 100 m tick / stop simulation.
 * Keep QueueGuideScreen.kt (v7) for the actual NaviHelper mode.
 *
 * DEMO distance model: total approach to the related junction 1300 m;
 * congestion begins after driving 900 m and ends at 1100 m.
 */
private const val MAP_JUNCTION_DISTANCE = 1300
private const val MAP_CONGESTION_START = 900
private const val MAP_CONGESTION_END = 1100

private val MapPage = Color(0xFFF2F5F8)
private val MapInk = Color(0xFF172638)
private val MapMuted = Color(0xFF627187)
private val MapTeal = Color(0xFF12695F)
private val MapAmber = Color(0xFF9E4D0B)
private val MapAmberSoft = Color(0xFFFFEED4)
private val MapCalmSoft = Color(0xFFE9F5F1)
private val MapBorder = Color(0xFFDDE4EC)

@Composable
fun QueueGuideMapDemoScreen(
    state: QueueGuideUiState,
    demoStep: Int,
    onReplay: () -> Unit
) {
    // Derive position only from the already verified v4 simulation state.
    // No independent timer: the vehicle and information panel cannot drift apart.
    val traveled = if (demoStep == 4) {
        MAP_CONGESTION_END
    } else {
        (MAP_JUNCTION_DISTANCE - (state.junctionMeters ?: MAP_JUNCTION_DISTANCE))
            .coerceIn(0, MAP_CONGESTION_END)
    }
    val animatedTravel = animateFloatAsState(
        targetValue = traveled.toFloat(),
        animationSpec = tween(durationMillis = 850),
        label = "demoCarTravel"
    ).value

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(MapPage)
    ) {
        val horizontal = maxWidth >= 800.dp
        if (horizontal) {
            Row(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                DemoRealMap(
                    traveledMeters = animatedTravel,
                    isStopped = state.isStopped,
                    modifier = Modifier.weight(1.30f).fillMaxHeight()
                )
                DemoSidePanel(
                    state = state,
                    step = demoStep,
                    onReplay = onReplay,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DemoRealMap(
                    traveledMeters = animatedTravel,
                    isStopped = state.isStopped,
                    modifier = Modifier.fillMaxWidth().height(330.dp)
                )
                DemoSidePanel(
                    state = state,
                    step = demoStep,
                    onReplay = onReplay,
                    modifier = Modifier.fillMaxWidth(),
                    scrollable = false
                )
            }
        }
    }
}

/**
 * Screenshot asset: app/src/main/res/drawable-nodpi/queueguide_jamsil_map.png.
 * Image cropped without rescaling from the user-provided map screenshot:
 * original (2048 x 1702), crop left=125, top=205, right=1125, bottom=1405.
 * Coordinates below are original image pixels. These are illustrative overlay
 * points visually aligned to the eastbound Olympic-daero / southbound ramp.
 * They are NOT generated by routing software.
 */
private const val SCREENSHOT_CROP_LEFT = 125f
private const val SCREENSHOT_CROP_TOP = 205f
private const val SCREENSHOT_WIDTH = 1000f
private const val SCREENSHOT_HEIGHT = 1200f

private data class DemoRoutePoint(val meter: Float, val x: Float, val y: Float)

private val demoRoadPoints = listOf(
    DemoRoutePoint(0f, 240f, 588f),
    DemoRoutePoint(100f, 338f, 608f),
    DemoRoutePoint(200f, 432f, 631f),
    DemoRoutePoint(300f, 527f, 650f),
    DemoRoutePoint(400f, 617f, 680f),
    DemoRoutePoint(500f, 686f, 702f),
    DemoRoutePoint(600f, 736f, 719f),
    DemoRoutePoint(700f, 784f, 758f),
    DemoRoutePoint(800f, 822f, 794f),
    DemoRoutePoint(900f, 858f, 831f),
    DemoRoutePoint(1000f, 900f, 877f),
    DemoRoutePoint(1100f, 933f, 928f),
    DemoRoutePoint(1200f, 941f, 1025f),
    DemoRoutePoint(1300f, 953f, 1130f)
)

private fun demoPointAt(meter: Float): Offset {
    val safe = meter.coerceIn(0f, MAP_JUNCTION_DISTANCE.toFloat())
    val index = demoRoadPoints.indexOfLast { it.meter <= safe }
        .coerceIn(0, demoRoadPoints.lastIndex - 1)
    val a = demoRoadPoints[index]
    val b = demoRoadPoints[index + 1]
    val t = ((safe - a.meter) / (b.meter - a.meter)).coerceIn(0f, 1f)
    return Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
}

/** Identical crop transformation to Image(ContentScale.Crop, Alignment.Center). */
private fun mapToViewport(p: Offset, canvasWidth: Float, canvasHeight: Float): Offset {
    val scale = max(canvasWidth / SCREENSHOT_WIDTH, canvasHeight / SCREENSHOT_HEIGHT)
    val xOffset = (canvasWidth - SCREENSHOT_WIDTH * scale) * 0.5f
    val yOffset = (canvasHeight - SCREENSHOT_HEIGHT * scale) * 0.5f
    return Offset(
        (p.x - SCREENSHOT_CROP_LEFT) * scale + xOffset,
        (p.y - SCREENSHOT_CROP_TOP) * scale + yOffset
    )
}

@Composable
private fun DemoRealMap(
    traveledMeters: Float,
    isStopped: Boolean,
    modifier: Modifier
) {
    BoxWithConstraints(
        modifier = modifier.clip(RoundedCornerShape(22.dp))
            .background(Color(0xFFE1E8E4))
            .border(1.dp, Color(0xFFCCDDD6), RoundedCornerShape(22.dp))
    ) {
        Image(
            painter = painterResource(R.drawable.queueguide_jamsil_map),
            contentDescription = "잠실종합운동장 일대 실제 지도 캡처 · 시연용 경로",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alignment = Alignment.Center
        )

        Canvas(Modifier.fillMaxSize()) {
            fun p(meter: Float) = mapToViewport(demoPointAt(meter), size.width, size.height)

            val route = Path().apply {
                val begin = p(0f)
                moveTo(begin.x, begin.y)
                for (m in 10..1300 step 10) {
                    val pt = p(m.toFloat())
                    lineTo(pt.x, pt.y)
                }
            }
            // White halo keeps blue route visible on both orange and pale roads.
            drawPath(route, Color.White.copy(alpha = 0.92f),
                style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(route, Color(0xFF1976D2),
                style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

            val jam = Path().apply {
                val begin = p(MAP_CONGESTION_START.toFloat())
                moveTo(begin.x, begin.y)
                for (m in MAP_CONGESTION_START + 10..MAP_CONGESTION_END step 10) {
                    val pt = p(m.toFloat())
                    lineTo(pt.x, pt.y)
                }
            }
            drawPath(jam, Color(0xFFC85D12),
                style = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawCircle(Color.White, 8.dp.toPx(), center = p(900f))
            drawCircle(Color(0xFFC85D12), 5.5.dp.toPx(), center = p(900f))

            val now = traveledMeters.coerceIn(0f, MAP_CONGESTION_END.toFloat())
            val car = p(now)
            val before = p((now - 5).coerceAtLeast(0f))
            val after = p((now + 5).coerceAtMost(MAP_JUNCTION_DISTANCE.toFloat()))
            val dx = after.x - before.x
            val dy = after.y - before.y
            val magnitude = hypot(dx, dy).coerceAtLeast(0.001f)
            val forward = Offset(dx / magnitude, dy / magnitude)
            val side = Offset(-forward.y, forward.x)
            val r = 20.dp.toPx()

            drawCircle(Color(0x8830466B), r + 4.dp.toPx(), center = car)
            drawCircle(Color.White, r + 1.dp.toPx(), center = car)
            drawCircle(Color(0xFF1B79D1), r - 2.dp.toPx(), center = car)
            val tip = car + forward * (r * 0.68f)
            val tail = car - forward * (r * 0.50f)
            val left = tail + side * (r * 0.43f)
            val right = tail - side * (r * 0.43f)
            val arrow = Path().apply {
                moveTo(tip.x, tip.y)
                lineTo(left.x, left.y)
                lineTo(car.x - forward.x * r * 0.13f, car.y - forward.y * r * 0.13f)
                lineTo(right.x, right.y)
                close()
            }
            drawPath(arrow, Color.White)
        }

        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val jamPos = mapToViewport(demoPointAt(900f), widthPx, heightPx)
        Box(
            modifier = Modifier.offset(
                x = with(density) { jamPos.x.toDp() } - 85.dp,
                y = with(density) { jamPos.y.toDp() } - 42.dp
            )
                .background(Color(0xEE8E4417), RoundedCornerShape(9.dp))
                .padding(horizontal = 9.dp, vertical = 5.dp)
        ) {
            Text("정체 시작점", color = Color.White, fontSize = 13.sp,
                fontWeight = FontWeight.Bold)
        }

        Column(
            modifier = Modifier.align(Alignment.TopStart).padding(12.dp)
                .background(Color(0xEFFFFFFF), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text("올림픽대로 · 잠실 일대", color = MapInk,
                fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text("종합운동장 분기점 · 우측 진출 시연", color = MapMuted, fontSize = 12.sp)
        }

        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)
                .background(Color(0xEDFFFFFF), RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(if (isStopped) "● 가상 차량 정차 중" else "● 가상 차량 주행 중",
                color = MapInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text("지도: © OpenStreetMap contributors | 경로·정체 위치는 시연 예시",
                color = MapMuted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun DemoSidePanel(
    state: QueueGuideUiState,
    step: Int,
    onReplay: () -> Unit,
    modifier: Modifier,
    scrollable: Boolean = true
) {
    val isAlert = state.phase == GuidePhase.APPROACH || state.phase == GuidePhase.IN_CONGESTION
    val after = step == 4
    val direction = state.junctionGuide.substringBefore(" · ").ifBlank { "우측 진출" }
    val destination = state.junctionGuide.substringAfter(" · ", "종합운동장IC 방면")

    val panelModifier = if (scrollable) modifier.verticalScroll(rememberScrollState()) else modifier
    Column(modifier = panelModifier,
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("QueueGuide", color = MapInk, fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold)
                Text("분기점 정체 사전 안내", color = MapMuted, fontSize = 14.sp)
            }
            Box(Modifier.background(Color(0xFFE6ECF3), RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 7.dp)) {
                Text("DEMO · ${if (state.isStopped) 0 else if (after) 0 else 72} km/h",
                    color = MapMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Dominant card: stable at every step, following the v7 design decision.
        Card(
            modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFFB5DACE), RoundedCornerShape(21.dp)),
            shape = RoundedCornerShape(21.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("정체 관련 분기점 안내", color = MapTeal, fontWeight = FontWeight.Bold,
                    fontSize = 16.sp)
                Text("시연 목적지 · 잠실종합운동장", color = MapMuted, fontSize = 14.sp)
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier.size(61.dp)
                            .background(MapCalmSoft, RoundedCornerShape(15.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("↱", color = MapTeal, fontSize = 40.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(direction, color = MapInk, fontSize = 27.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(destination, color = MapMuted, fontSize = 15.sp,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Text(state.junctionMeters?.let(::demoFormatDistance) ?: "—",
                        color = MapInk, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth().border(
                1.dp, if (isAlert) Color(0xFFECB97A) else Color(0xFFB9DDCF),
                RoundedCornerShape(20.dp)
            ),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = if (isAlert) MapAmberSoft else MapCalmSoft)
        ) {
            Column(Modifier.padding(20.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    when {
                        after -> "✓ 정체 구간 통과 완료"
                        state.isStopped -> "■ 정차 중 · 경고 유지"
                        state.phase == GuidePhase.IN_CONGESTION -> "정체 구간 진행 중"
                        isAlert -> "⚠ 전방 정체 주의"
                        else -> "전방 정체 모니터링 중"
                    },
                    color = if (isAlert) MapAmber else MapTeal,
                    fontSize = 21.sp, fontWeight = FontWeight.Bold
                )
                if (after) {
                    Text("정체 종료점을 통과하여 경고가 해제되었습니다.",
                        color = MapMuted, fontSize = 16.sp)
                } else {
                    val distance = if (state.phase == GuidePhase.IN_CONGESTION)
                        state.congestionEndMeters else state.congestionStartMeters
                    Row(modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(if (state.phase == GuidePhase.IN_CONGESTION) "정체 종료점까지"
                        else "정체 시작점까지", color = MapInk, fontSize = 16.sp)
                        Text(distance?.let(::demoFormatDistance) ?: "—",
                            color = if (isAlert) MapAmber else MapTeal,
                            fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    if (isAlert) {
                        Text(
                            if (state.isStopped) "정차 중에는 거리 감소가 멈춥니다."
                            else "주변 교통 상황을 확인하며 안전하게 주행하세요.",
                            color = MapMuted, fontSize = 14.sp
                        )
                    }
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val phases = listOf("일반", "접근", "진입", "정차", "통과")
            phases.forEachIndexed { index, name ->
                Column(modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth().height(6.dp)
                        .background(if (index > step) MapBorder
                        else if (index == 0 || index == 4) MapTeal
                        else Color(0xFFE49A47), RoundedCornerShape(7.dp)))
                    Spacer(Modifier.height(5.dp))
                    Text(name, color = if (step == index) MapInk else MapMuted,
                        fontSize = 13.sp,
                        fontWeight = if (step == index) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }

        Text("시연용 가상 지도·교통 정보 · 실제 혼잡 상태와 무관",
            modifier = Modifier.fillMaxWidth(),
            fontSize = 13.sp, color = MapMuted, textAlign = TextAlign.Center)
        if (after) {
            Button(onClick = onReplay, modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(13.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MapInk)) {
                Text("시연 다시 보기", fontSize = 17.sp)
            }
        }
    }
}

private fun demoFormatDistance(meters: Int): String {
    val safe = meters.coerceAtLeast(0)
    return if (safe >= 1000) String.format(Locale.KOREA, "%.1f km", safe / 1000.0)
    else "$safe m"
}
