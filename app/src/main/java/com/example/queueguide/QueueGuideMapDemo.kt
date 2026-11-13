package com.example.queueguide

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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

/**
 * Fictional urban right-turn demonstration, NOT an actual GPS route or lane-specific
 * congestion observation. Live NaviHelper mode remains in QueueGuideScreen.kt.
 *
 * The demo begins 1300 m before a right turn; congestion starts 900 m ahead,
 * extends to the turn at 1300 m, and is fully cleared at the final demo step.
 * MainActivity owns all simulated positions and warning state.
 *
 * Drop-in replacement for QueueGuideMapDemo.kt (do not keep both files declaring
 * QueueGuideMapDemoScreen in the same source set).
 */
private const val MAP_JUNCTION_DISTANCE = 1300
private const val MAP_CONGESTION_START = 900
private const val MAP_CONGESTION_END = 1300

private val Ink = Color(0xFF162538)
private val Muted = Color(0xFF66768A)
private val Teal = Color(0xFF176D60)
private val Amber = Color(0xFF9E4D0B)
private val AmberSoft = Color(0xFFFFF0DD)
private val CalmSoft = Color(0xFFEAF5F1)
private val Border = Color(0xFFE1E8EF)

@Composable
fun QueueGuideMapDemoScreen(
    state: QueueGuideUiState,
    demoStep: Int,
    onReplay: () -> Unit
) {
    // Single source of truth: MainActivity.demoUiState. No extra timer.
    val traveled = if (demoStep == 4) MAP_CONGESTION_END else {
        (MAP_JUNCTION_DISTANCE - (state.junctionMeters ?: MAP_JUNCTION_DISTANCE))
            .coerceIn(0, MAP_CONGESTION_END)
    }
    val animatedTravel = animateFloatAsState(
        targetValue = traveled.toFloat(),
        animationSpec = tween(durationMillis = 800),
        label = "cityRightTurnDemoProgress"
    ).value

    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFFF3F6F9))) {
        if (maxWidth >= 800.dp) {
            Row(
                modifier = Modifier.fillMaxSize().padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                CityTurnNavigationMap(
                    traveledMeters = animatedTravel,
                    demoStep = demoStep,
                    isStopped = state.isStopped,
                    modifier = Modifier.weight(1.24f).fillMaxHeight()
                )
                CityTurnSidePanel(
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
                CityTurnNavigationMap(
                    traveledMeters = animatedTravel,
                    demoStep = demoStep,
                    isStopped = state.isStopped,
                    modifier = Modifier.fillMaxWidth().height(390.dp)
                )
                CityTurnSidePanel(
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

/** Cartographic-style 2D vector map. Camera follows the car as it approaches. */
@Composable
private fun CityTurnNavigationMap(
    traveledMeters: Float,
    demoStep: Int,
    isStopped: Boolean,
    modifier: Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, Color(0xFFD5DEE6), RoundedCornerShape(20.dp))
            .background(Color(0xFFE7EAE2))
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val s = size.width / 1000f
            val afterTurn = demoStep == 4
            val carX = if (afterTurn) 724f else 610f
            val carY = if (afterTurn) 315f else
                1750f - traveledMeters.coerceIn(0f, MAP_JUNCTION_DISTANCE.toFloat()) *
                        (1365f / MAP_JUNCTION_DISTANCE)
            val cameraY = carY - size.height * 0.78f / s
            fun p(x: Float, y: Float) = Offset(x * s, (y - cameraY) * s)
            fun line(x1: Float, y1: Float, x2: Float, y2: Float,
                     color: Color, width: Float, cap: StrokeCap = StrokeCap.Butt) {
                drawLine(color, p(x1, y1), p(x2, y2), width * s, cap)
            }
            fun rect(x: Float, y: Float, w: Float, h: Float, color: Color, rad: Float = 0f) {
                drawRoundRect(
                    color = color,
                    topLeft = p(x, y),
                    size = Size(w * s, h * s),
                    cornerRadius = CornerRadius(rad * s, rad * s)
                )
            }
            // Base land and green areas. Colors are intentionally navigation-like.
            drawRect(Color(0xFFE7EBE5))
            for (y in -900..2200 step 600) {
                rect(12f, y.toFloat() + 12f, 300f, 460f, Color(0xFFE0E9D9), 12f)
                rect(750f, y.toFloat() + 12f, 235f, 460f, Color(0xFFF0ECE3), 12f)
                // Buildings with soft shadows, courtyards and varied footprints.
                rect(34f, y + 42f, 118f, 132f, Color(0xFFD2D9D4), 9f)
                rect(40f, y + 36f, 112f, 128f, Color(0xFFFAF9F5), 9f)
                rect(165f, y + 58f, 118f, 92f, Color(0xFFFBFAF6), 9f)
                rect(40f, y + 219f, 232f, 178f, Color(0xFFF9F7F1), 12f)
                rect(762f, y + 45f, 184f, 126f, Color(0xFFF9F7F2), 9f)
                rect(770f, y + 216f, 76f, 185f, Color(0xFFFDFBF7), 8f)
                rect(861f, y + 219f, 109f, 183f, Color(0xFFFAF8F3), 8f)
                rect(52f, y + 423f, 37f, 12f, Color(0xFFB3D0B5), 6f)
                rect(120f, y + 421f, 65f, 10f, Color(0xFFB3D0B5), 5f)
            }

            // Side streets and crossings, including the upcoming major intersection.
            for (y in listOf(815f, 1270f, 1840f)) {
                rect(0f, y, 1000f, 110f, Color(0xFFCDD3D3))
                rect(0f, y + 10f, 1000f, 90f, Color(0xFFFAFAF8))
                for (x in 0..1000 step 110)
                    line(x.toFloat(), y + 54f, x + 42f, y + 54f,
                        Color(0xFFE4E6E3), 3f)
            }

            // Main 4-lane road, sidewalks, then the east-west road to turn into.
            rect(339f, -1000f, 386f, 3400f, Color(0xFFBDC9C9))
            rect(352f, -1000f, 360f, 3400f, Color(0xFFF9FBFC))
            rect(355f, -1000f, 354f, 3400f, Color(0xFFDFE3E5))
            rect(0f, 227f, 1200f, 190f, Color(0xFFBAC4C5))
            rect(0f, 239f, 1200f, 166f, Color(0xFFE1E5E6))
            // Intersection surface.
            rect(355f, 239f, 354f, 166f, Color(0xFFDFE3E5))
            // Two-way traffic divider on the central arterial.
            for (y in -1000..2350 step 100) {
                line(530f, y.toFloat(), 530f, y + 54f, Color(0xFFFFF8E8), 5f)
            }
            for (y in -1000..2350 step 95) {
                line(443f, y.toFloat(), 443f, y + 42f, Color.White, 4f)
                line(619f, y.toFloat(), 619f, y + 42f, Color.White, 4f)
            }
            // East-west guide markings.
            for (x in 0..1050 step 110) {
                line(x.toFloat(), 321f, x + 52f, 321f, Color.White, 3.5f)
            }
            // Crosswalks to make the intersection recognizable, positioned before stop line.
            for (x in 375..685 step 35) {
                rect(x.toFloat(), 437f, 20f, 43f, Color(0xFFF8FCFF), 2f)
            }
            for (y in 265..375 step 34) {
                rect(741f, y.toFloat(), 40f, 19f, Color(0xFFF9FCFD), 2f)
            }
            line(532f, 490f, 705f, 490f, Color.White, 5f)

            // Route: northbound then an unmistakable RIGHT turn (toward screen right).
            val route = Path().apply {
                val a = p(619f, 2210f)
                moveTo(a.x, a.y)
                val b = p(619f, 391f)
                lineTo(b.x, b.y)
                val c = p(634f, 318f)
                val d = p(714f, 318f)
                quadraticBezierTo(c.x, c.y, d.x, d.y)
                val e = p(1220f, 318f)
                lineTo(e.x, e.y)
            }
            drawPath(route, Color.White, style = Stroke(
                width = 30f * s, cap = StrokeCap.Round, join = StrokeJoin.Round
            ))
            drawPath(route, Color(0xFF2481E9), style = Stroke(
                width = 19f * s, cap = StrokeCap.Round, join = StrokeJoin.Round
            ))

            // Orange lane-level *illustration*; not data confirmed per lane by SDK.
            val queueOutline = Path().apply {
                val q0 = p(674f, 790f)
                val q1 = p(674f, 397f)
                val q2 = p(710f, 325f)
                moveTo(q0.x, q0.y)
                lineTo(q1.x, q1.y)
                quadraticBezierTo(q1.x, q2.y, q2.x, q2.y)
            }
            drawPath(queueOutline, Color(0xFFFAF9F7),
                style = Stroke(width = 27f * s, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(queueOutline, Color(0xFFF08332),
                style = Stroke(width = 20f * s, cap = StrokeCap.Round, join = StrokeJoin.Round))
            val jamStart = p(674f, 790f)
            drawCircle(Color.White, 14f*s, jamStart)
            drawCircle(Color(0xFFE87926), 10f*s, jamStart)
            // Queue of orange vehicles aligned with the northbound rightmost lane.
            for (y in 492..746 step 45) {
                rect(660f, y.toFloat(), 28f, 33f, Color(0xFFD35B37), 6f)
                rect(664f, y + 5f, 20f, 6f, Color(0xFFF6C7AD), 2f)
            }

            // Vehicle is camera-anchored, so the road scrolls while vehicle stays legible.
            val car = p(carX, carY)
            drawCircle(Color(0x663A5A77), 37f*s, car)
            drawCircle(Color.White, 32f*s, car)
            drawCircle(Color(0xFF1B78D4), 27f*s, car)
            val a = Path().apply {
                if (afterTurn) {
                    moveTo(car.x + 19f*s, car.y)
                    lineTo(car.x - 11f*s, car.y - 13f*s)
                    lineTo(car.x - 5f*s, car.y)
                    lineTo(car.x - 11f*s, car.y + 13f*s)
                } else {
                    moveTo(car.x, car.y - 19f*s)
                    lineTo(car.x - 13f*s, car.y + 11f*s)
                    lineTo(car.x, car.y + 5f*s)
                    lineTo(car.x + 13f*s, car.y + 11f*s)
                }
                close()
            }
            drawPath(a, Color.White)
        }

        Column(
            modifier = Modifier.align(Alignment.TopStart).padding(12.dp)
                .background(Color(0xF5FFFFFF), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text("도심 간선도로 · 가상 시연", fontSize = 16.sp,
                fontWeight = FontWeight.Bold, color = Ink)
            Text("전방 교차로 우회전 · 대기행렬 접근", fontSize = 12.sp, color = Muted)
        }

        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)
                .background(Color(0xEEFFFFFF), RoundedCornerShape(11.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                if (isStopped) "● 차량 정차 · 거리 유지"
                else if (demoStep == 4) "✓ 정체 구간 통과"
                else "● 가상 주행 화면",
                color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 13.sp
            )
            Text("지도·대기행렬은 개념도이며 실제 GPS/차로 데이터가 아닙니다.",
                color = Muted, fontSize = 10.sp, maxLines = 2)
        }
    }
}

@Composable
private fun CityTurnSidePanel(
    state: QueueGuideUiState,
    step: Int,
    onReplay: () -> Unit,
    modifier: Modifier,
    scrollable: Boolean = true
) {
    val warning = state.phase == GuidePhase.APPROACH ||
            state.phase == GuidePhase.IN_CONGESTION
    val passed = step == 4
    val navLabel = state.junctionGuide.ifBlank { "우회전 · 다음 교차로" }
    val title = navLabel.substringBefore(" · ").ifBlank { "우회전" }
    val caption = navLabel.substringAfter(" · ", "다음 교차로")

    val panelModifier = if (scrollable)
        modifier.verticalScroll(rememberScrollState()) else modifier
    Column(
        panelModifier, verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("QueueGuide", fontSize = 27.sp,
                    fontWeight = FontWeight.ExtraBold, color = Ink)
                Text("도심 교차로 정체 사전 안내", fontSize = 13.sp, color = Muted)
            }
            // MainActivity의 시연 거리와 동일한 속도 프로필을 사용한다.
            val traveledMeters = (
                    MAP_JUNCTION_DISTANCE - (state.junctionMeters ?: MAP_JUNCTION_DISTANCE)
                    ).coerceIn(0, MAP_JUNCTION_DISTANCE)
            val demoSpeed = if (passed) 0 else demoSpeedKmhAt(traveledMeters, state.isStopped)
            Text(if (passed) "DEMO · 완료" else "DEMO · $demoSpeed km/h · 4배속",
                color = Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }

        Card(
            modifier = Modifier.fillMaxWidth().border(
                1.dp, Color(0xFFB4DBD2), RoundedCornerShape(19.dp)),
            shape = RoundedCornerShape(19.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(Modifier.fillMaxWidth().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("다음 경로 안내", color = Teal,
                    fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("↱", fontSize = 42.sp, color = Teal, fontWeight = FontWeight.Bold)
                    Column(Modifier.weight(1f)) {
                        Text(title, fontSize = 26.sp, color = Ink,
                            fontWeight = FontWeight.ExtraBold, maxLines = 1)
                        Text(caption, fontSize = 14.sp, color = Muted,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Text(
                        state.junctionMeters?.let(::cityFormatDistance) ?: "—",
                        fontSize = 25.sp, color = Ink, fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth().border(
                1.dp, if (warning) Color(0xFFEBB77B) else Color(0xFFB4DDD1),
                RoundedCornerShape(19.dp)),
            shape = RoundedCornerShape(19.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (warning) AmberSoft else CalmSoft)
        ) {
            Column(Modifier.fillMaxWidth().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    when {
                        passed -> "✓ 정체 구간 통과"
                        state.isStopped -> "■ 정차 중 · 안내 유지"
                        state.phase == GuidePhase.IN_CONGESTION -> "정체 구간 진행 중"
                        warning -> "⚠ 정체 시작점 접근 중"
                        else -> "전방 교통상황 모니터링"
                    },
                    color = if (warning) Amber else Teal,
                    fontSize = 19.sp, fontWeight = FontWeight.Bold
                )
                if (passed) {
                    Text("정체 종료점을 통과하여 경고가 해제되었습니다.",
                        color = Muted, fontSize = 15.sp)
                } else {
                    val inside = state.phase == GuidePhase.IN_CONGESTION
                    val remaining = if (inside) state.congestionEndMeters
                    else state.congestionStartMeters
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (inside) "정체 종료점까지" else "정체 시작점까지",
                            fontSize = 14.sp, color = Ink)
                        Text(remaining?.let(::cityFormatDistance) ?: "—",
                            fontSize = 30.sp, fontWeight = FontWeight.ExtraBold,
                            color = if (warning) Amber else Teal)
                    }
                    Text(
                        if (state.isStopped) "정차 중에는 위치와 안내 정보를 유지합니다."
                        else if (warning) "무리한 끼어들기 없이 주변 교통상황을 확인하세요."
                        else "우회전 방향 정체를 미리 안내합니다.",
                        fontSize = 13.sp, color = Muted
                    )
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            // STEP 3: 정차 중에는 "정차", 재출발 후에는 "재출발"로 표기한다.
            listOf("일반", "접근", "진입", "정차", "통과").forEachIndexed { index, label ->
                val item = if (index == 3 && step == 3 && !state.isStopped) "재출발" else label
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.fillMaxWidth().height(6.dp).background(
                            if (index > step) Border
                            else if (index == 0 || index == 4) Teal
                            else Color(0xFFE79A42), RoundedCornerShape(5.dp)
                        )
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(item, fontSize = 12.sp,
                        color = if (index == step) Ink else Muted,
                        fontWeight = if (index == step) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }

        Text(
            "데모용 가상 정체 및 경로 · 실제 차로별 정체 감지 결과가 아닙니다.",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center, fontSize = 11.sp, color = Muted
        )
        if (passed) {
            Button(
                onClick = onReplay, modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Ink)
            ) {
                Text("시연 다시 보기", fontSize = 16.sp)
            }
        }
    }
}

private fun cityFormatDistance(meters: Int): String {
    val safe = meters.coerceAtLeast(0)
    return if (safe >= 1000)
        String.format(Locale.KOREA, "%.1f km", safe / 1000.0)
    else "$safe m"
}
