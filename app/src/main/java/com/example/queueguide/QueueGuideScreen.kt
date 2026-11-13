package com.example.queueguide

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

// Keep the same state model and public function signature as v6 / MainActivity v4.
enum class GuidePhase { WAITING, NORMAL, APPROACH, IN_CONGESTION }

data class QueueGuideUiState(
    val phase: GuidePhase = GuidePhase.WAITING,
    val congestionStartMeters: Int? = null,
    val congestionEndMeters: Int? = null,
    val junctionMeters: Int? = null,
    val junctionGuide: String = "",
    val isStopped: Boolean = false,
    val notice: String? = null
)

private val PageBg = Color(0xFFF3F5F8)
private val Ink = Color(0xFF15263A)
private val Muted = Color(0xFF526477)
private val Orange = Color(0xFFA75500)
private val OrangeSoft = Color(0xFFFFF0D8)
private val OrangeBorder = Color(0xFFEBC78F)
private val Calm = Color(0xFF12695F)
private val CalmSoft = Color(0xFFE8F4F1)
private val CalmBorder = Color(0xFFB4DBD1)
private val SoftPanel = Color(0xFFEEF2F7)

/**
 * UI only: the navigation-related junction is the visually dominant card.
 * The congestion warning is a secondary, color-changing card below it.
 * Actual warning decisions remain in MainActivity.
 */
@Composable
fun QueueGuideScreen(
    state: QueueGuideUiState,
    demoStep: Int? = null,
    onNextStep: (() -> Unit)? = null,
    onReplay: (() -> Unit)? = null
) {
    val isAlert = state.phase == GuidePhase.APPROACH ||
            state.phase == GuidePhase.IN_CONGESTION
    val passed = state.phase == GuidePhase.NORMAL && state.notice == "정체 구간 통과"
    val hasJunction = state.junctionGuide.isNotBlank() || state.junctionMeters != null

    // MainActivity currently supplies the congestion-related TBT, which is not
    // necessarily the very next navigation maneuver.
    val direction = state.junctionGuide.substringBefore(" · ").ifBlank { "분기점 안내" }
    val destination = state.junctionGuide.substringAfter(" · ", "").trim()
    val directionIcon = when {
        direction.contains("좌회전") -> "↰"
        direction.contains("우회전") -> "↱"
        direction.contains("유턴") -> "↶"
        direction.contains("직진") -> "↑"
        else -> "↗"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier.widthIn(max = 880.dp).fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("QueueGuide", fontSize = 27.sp,
                        fontWeight = FontWeight.ExtraBold, color = Ink)
                    Text("분기점 정체 사전 안내", fontSize = 14.sp, color = Muted)
                }
                if (demoStep != null) {
                    Box(
                        modifier = Modifier.background(SoftPanel, RoundedCornerShape(12.dp))
                            .padding(horizontal = 13.dp, vertical = 8.dp)
                    ) {
                        Text(
                            when {
                                demoStep == 4 -> "DEMO · 시연 완료"
                                state.isStopped -> "DEMO · 0 km/h · 정차"
                                else -> "DEMO · 72 km/h"
                            },
                            color = Muted, fontSize = 14.sp, fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(Modifier.height(17.dp))

            // PRIMARY: the navigation-related maneuver remains in the same place
            // whether the congestion alert is on or off.
            Card(
                modifier = Modifier.fillMaxWidth()
                    .border(1.dp, CalmBorder, RoundedCornerShape(23.dp)),
                shape = RoundedCornerShape(23.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Box(
                    Modifier.fillMaxWidth().height(7.dp).background(Calm)
                )
                Column(Modifier.fillMaxWidth().padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.background(CalmSoft, RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Text("경로 안내", color = Calm, fontSize = 16.sp,
                                fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(10.dp))
                        Text("정체 관련 분기점", fontSize = 15.sp, color = Muted)
                    }
                    Spacer(Modifier.height(17.dp))

                    if (hasJunction) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(
                                modifier = Modifier.width(78.dp).height(78.dp)
                                    .background(CalmSoft, RoundedCornerShape(18.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(directionIcon, fontSize = 53.sp,
                                    fontWeight = FontWeight.Bold, color = Calm)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    direction,
                                    color = Ink, fontSize = 32.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (destination.isNotBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        destination,
                                        color = Muted, fontSize = 18.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Text(
                                state.junctionMeters?.let(::formatDistance) ?: "—",
                                color = Ink, fontSize = 36.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1
                            )
                        }
                    } else {
                        Text(
                            if (state.phase == GuidePhase.WAITING)
                                "경로 안내 대기 중" else "관련 분기점 확인 중",
                            fontSize = 29.sp,
                            fontWeight = FontWeight.Bold, color = Ink
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (state.phase == GuidePhase.WAITING)
                                "내비게이션 경로 정보를 기다리고 있습니다."
                            else "정체와 관련된 분기점 정보가 아직 없습니다.",
                            fontSize = 17.sp, color = Muted
                        )
                    }
                }
            }

            Spacer(Modifier.height(15.dp))

            // SECONDARY: the congestion panel changes state without moving the
            // primary junction instructions.
            Card(
                modifier = Modifier.fillMaxWidth().border(
                    1.dp,
                    if (isAlert) OrangeBorder else CalmBorder,
                    RoundedCornerShape(22.dp)
                ),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isAlert) OrangeSoft else CalmSoft
                )
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 21.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (isAlert) "⚠" else "●",
                            color = if (isAlert) Orange else Calm,
                            fontSize = 24.sp
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            when {
                                state.phase == GuidePhase.APPROACH -> "전방 정체 주의"
                                state.phase == GuidePhase.IN_CONGESTION -> "정체 구간 진행 중"
                                passed -> "정체 구간 통과 완료"
                                state.phase == GuidePhase.WAITING -> "정체 안내 대기 중"
                                else -> "전방 정체 모니터링 중"
                            },
                            color = if (isAlert) Orange else Calm,
                            fontSize = 23.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(13.dp))

                    val distanceLabel = when {
                        state.phase == GuidePhase.IN_CONGESTION -> "정체 종료점까지"
                        else -> "정체 시작점까지"
                    }
                    val distance = when (state.phase) {
                        GuidePhase.IN_CONGESTION -> state.congestionEndMeters
                        else -> state.congestionStartMeters
                    }
                    when {
                        passed -> {
                            Text("경고가 해제되었습니다. 경로 안내를 계속합니다.",
                                fontSize = 18.sp, color = Muted)
                        }
                        distance != null -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(distanceLabel, fontSize = 19.sp, color = Ink,
                                    fontWeight = FontWeight.Medium)
                                Text(
                                    formatDistance(distance),
                                    fontSize = if (isAlert) 39.sp else 30.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isAlert) Orange else Calm,
                                    maxLines = 1
                                )
                            }
                        }
                        else -> {
                            Text(
                                if (state.phase == GuidePhase.WAITING)
                                    "경로 설정 후 안내합니다."
                                else "정체 정보를 확인하고 있습니다.",
                                fontSize = 18.sp, color = Muted
                            )
                        }
                    }
                    if (isAlert) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (state.isStopped)
                                "정차 중 · 거리 유지 · 경고 유지"
                            else "주변 교통 상황을 확인하고 안전하게 주행하세요.",
                            fontSize = 16.sp, color = Muted
                        )
                    }
                }
            }

            if (demoStep != null) {
                Spacer(Modifier.height(18.dp))
                DemoTimeline(demoStep.coerceIn(0, 4))
                Spacer(Modifier.height(9.dp))
                Text(
                    "시연용 가상 데이터 · 실제 교통 상황과 무관",
                    modifier = Modifier.fillMaxWidth(),
                    color = Muted, fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
                if (demoStep == 4 && onReplay != null) {
                    Spacer(Modifier.height(13.dp))
                    Button(
                        onClick = onReplay,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Ink),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("시연 다시 보기", fontSize = 18.sp) }
                }
                if (onNextStep != null) {
                    TextButton(onClick = onNextStep, modifier = Modifier.align(Alignment.End)) {
                        Text("다음 단계 (개발용)", fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun DemoTimeline(step: Int) {
    val names = listOf("일반", "접근", "진입", "정차", "통과")
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        names.forEachIndexed { index, name ->
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(7.dp)
                        .background(
                            if (index > step) Color(0xFFDCE2E9)
                            else if (index == 0 || index == 4) Calm
                            else Orange,
                            RoundedCornerShape(8.dp)
                        )
                )
                Spacer(Modifier.height(7.dp))
                Text(name, fontSize = 15.sp,
                    fontWeight = if (index == step) FontWeight.Bold else FontWeight.Normal,
                    color = if (index == step) Ink else Muted)
            }
        }
    }
}

private fun formatDistance(meters: Int): String {
    val safe = meters.coerceAtLeast(0)
    return if (safe >= 1000) String.format(Locale.KOREA, "%.1f km", safe / 1000.0)
    else "$safe m"
}

/** Demonstration only. SDK/live warning decisions stay in MainActivity. */
fun demoQueueGuideUiState(step: Int): QueueGuideUiState = when (step.coerceIn(0, 4)) {
    0 -> QueueGuideUiState(
        phase = GuidePhase.NORMAL,
        congestionStartMeters = 900,
        junctionMeters = 1300,
        junctionGuide = "우회전 · 다음 교차로"
    )
    1 -> QueueGuideUiState(
        phase = GuidePhase.APPROACH,
        congestionStartMeters = 700,
        junctionMeters = 1100,
        junctionGuide = "우회전 · 다음 교차로"
    )
    2 -> QueueGuideUiState(
        phase = GuidePhase.IN_CONGESTION,
        congestionStartMeters = 0,
        congestionEndMeters = 400,
        junctionMeters = 400,
        junctionGuide = "우회전 · 다음 교차로"
    )
    3 -> QueueGuideUiState(
        phase = GuidePhase.IN_CONGESTION,
        congestionStartMeters = 0,
        congestionEndMeters = 300,
        junctionMeters = 300,
        junctionGuide = "우회전 · 다음 교차로",
        isStopped = true
    )
    else -> QueueGuideUiState(
        phase = GuidePhase.NORMAL,
        notice = "정체 구간 통과",
        junctionMeters = 0,
        junctionGuide = "우회전 · 다음 교차로"
    )
}
