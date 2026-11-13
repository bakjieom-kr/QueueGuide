package com.example.queueguide

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.delay
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.data.TBTInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import com.example.queueguide.ui.theme.QueueGuideTheme

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.SystemClock
import androidx.activity.result.contract.ActivityResultContracts


// true: 가상 주행 시연 / false: 실제 NaviHelper 경고 화면
private const val ENABLE_DEMO = true

// 도심 정체 시연용 속도: 거리별 감속, 정차, 재출발을 표현한다.
// 재생 시간은 약 4배속으로 압축하며 실제 SDK/GPS 속도 처리는 변경하지 않는다.
private const val DEMO_METERS_PER_TICK = 100
private const val DEMO_PLAYBACK_MULTIPLIER = 4.0
private const val DEMO_STOP_MILLIS = 4_000L
// 시연: 정체 시작점 900m, 우회전 교차로 1,300m / 정체 구간 400m
private const val DEMO_INITIAL_START_METERS = 900
private const val DEMO_INITIAL_JUNCTION_METERS = 1_300
private const val DEMO_ALERT_TRAVEL_METERS = 200  // 900 -> 800 -> 700m에서 경고 시작
private const val DEMO_STOP_TRAVEL_METERS = 1_000 // 정체 구간 100m 진입 후 정차
private const val DEMO_TOTAL_TRAVEL_METERS = 1_300

/** 시연에만 사용하는 예시 속도(km/h). 실제 NaviHelper 차량 속도와 무관. */
internal fun demoSpeedKmhAt(traveledMeters: Int, isStopped: Boolean = false): Int = when {
    isStopped -> 0
    traveledMeters < 800 -> 72      // 일반 주행
    traveledMeters < 900 -> 45      // 정체 시작점 접근
    traveledMeters < 1000 -> 25     // 정체 구간 진입
    traveledMeters < 1100 -> 12     // 정차 전후 서행 및 재출발
    traveledMeters < 1200 -> 15     // 서행
    else -> 20                     // 교차로 접근
}

class MainActivity : ComponentActivity() {

    private lateinit var naviHelper: NaviHelper
    private var listenerRegistered = false


    @Volatile
    private var latestSpeedKmh: Double? = null

    @Volatile
    private var speedUpdatedAt = 0L

    @Volatile
    private var routeGuiding = false

    private val warnedTbt = mutableSetOf<String>()
    private var activeWarning: ActiveWarning? = null

    @Volatile
    private var latestDestinationRemain: Double? = null

    private var locationManager: LocationManager? = null

    private val speedListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            latestSpeedKmh = if (location.hasSpeed()) {
                (location.speed.toDouble() * 3.6)
                    .coerceAtLeast(0.0)
            } else {
                null
            }

            speedUpdatedAt = SystemClock.elapsedRealtime()

            Log.d(
                "QueueGuideSpeed",
                "GPS_SPEED=${latestSpeedKmh ?: "UNAVAILABLE"}"
            )

            compareAndLog()
        }
    }


    private val locationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val fineGranted =
                permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true

            if (fineGranted) {
                startSpeedTracking()
            } else {
                Log.w("QueueGuideSpeed", "정확한 위치 권한 없음")
            }
        }




    @Volatile
    private var latestTraffic: List<CongestionSegment>? = null

    @Volatile
    private var latestTbt: List<Pair<String, Double>>? = null


    private val guideStatus = mutableStateOf("")
    private val warningActive = mutableStateOf(false)

    // 실제 NaviHelper, GPS 및 경고 이력과 독립된 시연 상태
    private val demoStep = mutableStateOf(0)
    private val demoRunId = mutableStateOf(0)
    private val demoUiState = mutableStateOf(demoQueueGuideUiState(0))

    // Compose는 이 상태만 읽는다. 경고 발생/종료는 기존 비교 로직이 결정한다.
    private val liveUiState = mutableStateOf(QueueGuideUiState())


    private val naviEventListener = object : NaviHelperEventListener {

        override fun onCurrentLocationInfo(info: CurrentLocationInfo) {
            Log.d("QueueGuideNavi", "위치 정보: $info")

        }

        override fun onDestinationInfo(info: DestinationInfo) {
            Log.d("QueueGuideNavi", "목적지 정보: $info")
            Log.d("QueueGuideVerify", "DEST_REMAIN=${info.remainDistance}")
            val remain = info.remainDistance.toDouble()
            if (remain >= 0.0) {
                latestDestinationRemain = remain
                compareAndLog()
            }
        }


        override fun onRouteStateInfo(info: RouteStateInfo) {
            // 1. 원본 데이터 확인
            Log.d("QueueGuideVerify", "ROUTE_INFO_RAW=$info")
            Log.d("QueueGuideVerify", "TRAFFIC_STATUS_RAW=${info.trafficStatus}")

            // 2. 데이터 누락 여부 확인
            val traffic = info.trafficStatus

            routeGuiding = info.naviMode.toString() == "ROUTE_GUIDING"

            if (!routeGuiding) {
                synchronized(this@MainActivity) {
                    latestTraffic = null
                    latestTbt = null
                    latestDestinationRemain = null
                    warnedTbt.clear()
                    activeWarning = null
                }
                hideWarning(waitingForRoute = true)
                Log.d("QueueGuideWarn", "ROUTE_RESET: 경로 데이터 및 경고 이력 초기화")
                return
            }

            // 경로 안내가 시작되면 기본 화면으로 전환한다. 이미 경고가 켜졌다면 유지.
            runOnUiThread {
                if (!warningActive.value && liveUiState.value.phase == GuidePhase.WAITING) {
                    liveUiState.value = QueueGuideUiState(phase = GuidePhase.NORMAL)
                }
            }

            val sections = traffic?.congestionItemList.orEmpty()

            Log.d("QueueGuideVerify", "TRAFFIC_NULL=${traffic == null}")
            Log.d("QueueGuideVerify", "LIST_NULL=${traffic?.congestionItemList == null}")

            // 3. 정체 구간 확인
            Log.d("QueueGuideTraffic", "전체 정체 구간: ${sections.size}개")

            sections.sortedBy { it.offset.toDouble() }
                .forEachIndexed { index, section ->
                    Log.d(
                        "QueueGuideTraffic",
                        "구간 ${index + 1}: " +
                                "offset=${section.offset}, " +
                                "length=${section.distance}, " +
                                "level=${section.trafficCongestionType}, " +
                                "road=${section.roadName}"
                    )
                }

            latestTraffic = sections.map {
                CongestionSegment(
                    start = it.offset.toDouble(),
                    length = it.distance.toDouble()
                )
            }


            compareAndLog()
        }

        override fun onTBTInfo(tbtInfoList: List<TBTInfo>) {
            super.onTBTInfo(tbtInfoList)

            var cumulativeDistance = 0.0
            val turnPoints = mutableListOf<Pair<String, Double>>()

            tbtInfoList.forEachIndexed { index, tbt ->
                cumulativeDistance += tbt.distance.toDouble()

                Log.d(
                    "QueueGuideTBT",
                    "TBT ${index + 1}: " +
                            "type=${tbt.type}, " +
                            "distance=${tbt.distance}, " +
                            "cumulative=$cumulativeDistance, " +
                            "description=${tbt.description}"
                )

                if (tbt.type.toString() != "TURN_ARRIVE") {
                    turnPoints.add(
                        "${tbt.type} (${tbt.description})" to cumulativeDistance
                    )
                }
            }

            Log.d("QueueGuideTBT", "TBT_TOTAL=$cumulativeDistance")
            Log.d("QueueGuideVerify", "TBT_TOTAL=$cumulativeDistance")

            latestTbt = turnPoints
            compareAndLog()
        }


    }




    // 경고는 발생 시 한 번 활성화하고, 정체 종료점 통과까지 유지한다.
    @Synchronized
    private fun compareAndLog() {
        if (!routeGuiding) return
        val destRemain = latestDestinationRemain ?: return

        // 이미 경고 중이라면 새 후보를 찾지 않고 통과 여부만 갱신한다.
        activeWarning?.let { active ->
            if (hasPassedCongestion(active, destRemain)) {
                Log.d("QueueGuideWarn", "WARN_UI_OFF: 정체 구간 통과, key=${active.key}")
                activeWarning = null
                hideWarning(passedCongestion = true)
            } else {
                showWarning(active, destRemain)
                return
            }
        }

        val traffic = latestTraffic ?: return
        val tbtPoints = latestTbt ?: return

        // 10초 이내 측정된 GPS 속도를 사용하고, 없으면 800m로 대체한다.
        val speed = latestSpeedKmh?.takeIf {
            SystemClock.elapsedRealtime() - speedUpdatedAt <= 10_000L
        }
        val warningDistance = speed?.let(::calculateWarningDistance) ?: 800.0
        Log.d("QueueGuideWarn", "speed=${speed ?: "UNAVAILABLE"}, " +
                "mode=${if (speed == null) "FALLBACK" else "DYNAMIC"}, " +
                "warningDistance=$warningDistance")

        // BEFORE / OVERLAP 모두 검토하되, 경고 거리 내에서 가장 먼저
        // 시작하는 정체 후보 하나만 화면에 표시한다.
        val candidate = tbtPoints.flatMap { (label, tbtDistance) ->
            findCongestionNearTbt(tbtDistance, traffic).map { match ->
                WarningCandidate(label, tbtDistance, match.segment, match.relation)
            }
        }.filter {
            it.segment.start > 0.0 && it.segment.start <= warningDistance
        }.minByOrNull { it.segment.start } ?: return

        // TBT 거리만을 키로 쓰면 주행 중 매번 달라지므로
        // 목적지 기준 상대 위치를 100m 단위로 묶어 중복을 줄인다.
        val approximatePosition =
            kotlin.math.round((destRemain - candidate.tbtDistance) / 100.0).toInt()
        val key = "${candidate.label}@$approximatePosition"
        if (!shouldEmitWarning(key, candidate.segment.start, warningDistance, warnedTbt)) {
            return
        }

        val active = ActiveWarning(
            key = key,
            label = candidate.label,
            congestionStartAtAlert = candidate.segment.start,
            congestionEndAtAlert = candidate.segment.start + candidate.segment.length,
            tbtDistanceAtAlert = candidate.tbtDistance,
            destinationRemainAtAlert = destRemain
        )
        activeWarning = active
        Log.w("QueueGuideWarn", "WARN_UI_ON: ${candidate.relation}, " +
                "start=${candidate.segment.start}, end=${active.congestionEndAtAlert}, " +
                "tbt=${candidate.tbtDistance}, speed=${speed ?: "UNAVAILABLE"}")
        showWarning(active, destRemain)
    }

    // 실제 경고와 시연이 함께 사용하는 구간 통과 판단 함수
    private fun hasPassedCongestion(active: ActiveWarning, remain: Double): Boolean {
        val traveled = (active.destinationRemainAtAlert - remain).coerceAtLeast(0.0)
        return active.congestionEndAtAlert - traveled <= 0.0
    }

    // 실제 경고와 시연에 동일한 화면 문구 사용
    private fun warningText(active: ActiveWarning, remain: Double): String {
        val traveled = (active.destinationRemainAtAlert - remain).coerceAtLeast(0.0)
        val startLeft = (active.congestionStartAtAlert - traveled).coerceAtLeast(0.0)
        val tbtLeft = (active.tbtDistanceAtAlert - traveled).coerceAtLeast(0.0)
        val heading = if (startLeft > 0.0) {
            "⚠️ 정체 시작점 접근 중"
        } else {
            "⚠️ 정체 구간 진행 중"
        }
        return "$heading\n" +
                "정체 시작까지: ${startLeft.toInt()}m\n" +
                "분기점까지: ${tbtLeft.toInt()}m\n" +
                "안내: ${active.label}"
    }

    // SDK/시연 ActiveWarning을 동일한 UI 모델로 변환한다.
    private fun toWarningUiState(
        active: ActiveWarning,
        destRemain: Double,
        stopped: Boolean
    ): QueueGuideUiState {
        val traveled = (active.destinationRemainAtAlert - destRemain).coerceAtLeast(0.0)
        val startLeft = (active.congestionStartAtAlert - traveled).coerceAtLeast(0.0)
        val tbtLeft = (active.tbtDistanceAtAlert - traveled).coerceAtLeast(0.0)
        val endLeft = (active.congestionEndAtAlert - traveled).coerceAtLeast(0.0)
        val direction = when {
            active.label.startsWith("TURN_LEFT") -> "좌회전"
            active.label.startsWith("TURN_RIGHT") -> "우회전"
            active.label.startsWith("TURN_UTURN") -> "유턴"
            active.label.startsWith("TURN_STRAIGHT") -> "직진"
            else -> "분기점 안내"
        }
        // 원본 TBT의 괄호 안 설명은 별도 추천 차로가 아닌 안내 문구로 사용한다.
        val description = active.label.substringAfter('(', "").substringBeforeLast(')', "").trim()
        val guide = if (description.isBlank()) direction else "$direction · $description"

        return QueueGuideUiState(
            phase = if (startLeft > 0.0) GuidePhase.APPROACH else GuidePhase.IN_CONGESTION,
            congestionStartMeters = startLeft.toInt(),
            congestionEndMeters = endLeft.toInt(),
            junctionMeters = tbtLeft.toInt(),
            junctionGuide = guide,
            isStopped = stopped
        )
    }

    private fun showWarning(active: ActiveWarning, destRemain: Double) {
        val message = warningText(active, destRemain)
        // 오래된 속도 샘플은 정차 판정에 사용하지 않는다.
        val recentSpeed = latestSpeedKmh?.takeIf {
            SystemClock.elapsedRealtime() - speedUpdatedAt <= 10_000L
        }
        val nextUiState = toWarningUiState(
            active, destRemain, stopped = recentSpeed != null && recentSpeed <= 1.0
        )
        runOnUiThread {
            warningActive.value = true
            guideStatus.value = message
            liveUiState.value = nextUiState
        }
    }

    // 거리 기반 자동 시연: 일반 단계에서 900 -> 800m로 갱신한 뒤
    // 700m에서 경고를 켠다 (초기 72km/h 경고 기준 = 780m, 100m 단위 갱신).
    // 정체 구간 길이 400m, 정체 진입 100m 후 4초 정차.
    // 주의: 이는 GPS/NaviHelper가 아닌 시연용 가상 주행 데이터이다.
    private fun updateDemoPosition(
        traveledMeters: Int,
        isStopped: Boolean = false,
        stopWasShown: Boolean = false
    ) {
        // 출발 후 200m 이동한 지점(정체 시작까지 700m)에서 경고가 켜진다.
        val mockWarning = ActiveWarning(
            key = "DEMO_CITY_RIGHT@1100",
            label = "TURN_RIGHT (다음 교차로)",
            congestionStartAtAlert = 700.0,
            congestionEndAtAlert = 1100.0,
            tbtDistanceAtAlert = 1100.0,
            destinationRemainAtAlert = 2000.0
        )

        val movedSinceAlert = (traveledMeters - DEMO_ALERT_TRAVEL_METERS).coerceAtLeast(0)
        val remain = 2000.0 - movedSinceAlert
        val passed = hasPassedCongestion(mockWarning, remain)
        val startLeft = (DEMO_INITIAL_START_METERS - traveledMeters).coerceAtLeast(0)
        val demoSpeed = demoSpeedKmhAt(traveledMeters, isStopped)
        val warningDistance = calculateWarningDistance(demoSpeed.toDouble())
        // 정차 후에는 STEP 3을 재출발 상태로 유지해 진행 단계가 역행하지 않게 한다.
        val step = when {
            passed -> 4
            isStopped || stopWasShown -> 3
            startLeft == 0 -> 2
            shouldTriggerWarning(startLeft.toDouble(), warningDistance) -> 1
            else -> 0
        }
        val visible = step in 1..3 && !passed
        demoStep.value = step
        demoUiState.value = when {
            step == 0 -> QueueGuideUiState(
                phase = GuidePhase.NORMAL,
                congestionStartMeters = startLeft,
                junctionMeters = (DEMO_INITIAL_JUNCTION_METERS - traveledMeters).coerceAtLeast(0),
                junctionGuide = "우회전 · 다음 교차로"
            )
            passed -> QueueGuideUiState(
                phase = GuidePhase.NORMAL,
                notice = "정체 구간 통과",
                junctionMeters = (DEMO_INITIAL_JUNCTION_METERS - traveledMeters).coerceAtLeast(0),
                junctionGuide = "우회전 · 다음 교차로"
            )
            else -> toWarningUiState(mockWarning, remain, stopped = isStopped)
                .copy(junctionGuide = "우회전 · 다음 교차로")
        }

        val endLeft = (DEMO_TOTAL_TRAVEL_METERS - traveledMeters).coerceAtLeast(0)
        val junctionLeft = (DEMO_INITIAL_JUNCTION_METERS - traveledMeters).coerceAtLeast(0)
        Log.d(
            "QueueGuideDemo",
            "STEP=$step, traveled=${traveledMeters}m, " +
                    "startLeft=${startLeft}m, endLeft=${endLeft}m, " +
                    "tbtLeft=${junctionLeft}m, " +
                    "speed=${if (passed) 0 else demoSpeed}km/h, " +
                    "UI=${if (visible) "ON" else "OFF"}, passed=$passed"
        )
    }

    private fun hideWarning(
        waitingForRoute: Boolean = false,
        passedCongestion: Boolean = false
    ) {
        runOnUiThread {
            warningActive.value = false
            guideStatus.value = ""
            liveUiState.value = when {
                waitingForRoute -> QueueGuideUiState(phase = GuidePhase.WAITING)
                passedCongestion -> QueueGuideUiState(
                    phase = GuidePhase.NORMAL, notice = "정체 구간 통과"
                )
                else -> QueueGuideUiState(phase = GuidePhase.NORMAL)
            }
        }
    }

    private fun startSpeedTracking() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            return
        }

        try {
            val manager = getSystemService(LocationManager::class.java)

            if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                Log.w("QueueGuideSpeed", "GPS 사용 불가")
                return
            }

            locationManager = manager

            manager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000L,
                0f,
                speedListener
            )

            Log.d("QueueGuideSpeed", "속도 수신 시작")

        } catch (e: Exception) {
            Log.e("QueueGuideSpeed", "속도 수신 오류", e)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startSpeedTracking()


        setContent {
            QueueGuideTheme {
                if (ENABLE_DEMO) {
                    // 타이머는 단계 전환용이 아니라 100m씩 움직이는 가상 주행용이다.
                    // 재시연 버튼을 누르면 runId가 바뀌어 이전 재생을 취소하고 재시작한다.
                    val runId = demoRunId.value
                    LaunchedEffect(runId) {
                        updateDemoPosition(0)  // STEP 0: 900m, 1.3km
                        for (distance in DEMO_METERS_PER_TICK..DEMO_TOTAL_TRAVEL_METERS step DEMO_METERS_PER_TICK) {
                            // 100m 이동 시간을 해당 구간의 가상 속도로 계산해 4배속 재생.
                            val midPoint = distance - DEMO_METERS_PER_TICK / 2
                            val segmentSpeedKmh = demoSpeedKmhAt(midPoint)
                            val travelMillis = (
                                    DEMO_METERS_PER_TICK * 3.6 * 1000.0 /
                                            segmentSpeedKmh / DEMO_PLAYBACK_MULTIPLIER
                                    ).toLong().coerceAtLeast(800L)
                            delay(travelMillis)
                            if (distance == DEMO_STOP_TRAVEL_METERS) {
                                // 정체 구간 100m 진입 후 정차, 위치와 거리 유지
                                updateDemoPosition(distance, isStopped = true)
                                delay(DEMO_STOP_MILLIS)
                                // 재출발: 같은 위치에서 속도만 복구하고 다음 이동시간은 서행속도를 반영
                                updateDemoPosition(distance, stopWasShown = true)
                            } else {
                                updateDemoPosition(
                                    distance,
                                    stopWasShown = distance > DEMO_STOP_TRAVEL_METERS
                                )
                            }
                        }
                    }
                    QueueGuideMapDemoScreen(
                        state = demoUiState.value,
                        demoStep = demoStep.value,
                        onReplay = { demoRunId.value += 1 }
                    )
                } else {
                    QueueGuideScreen(state = liveUiState.value)
                }
            }
        }


        naviHelper = NaviHelper(applicationContext)

        naviHelper.initialize(
            onConnected = {
                Log.d("QueueGuideNavi", "NaviHelper 연결 성공")


                try {
                    naviHelper.addListener(naviEventListener)
                    listenerRegistered = true

                    naviHelper.getCurrentLocationInfo()
                    naviHelper.getDestinationInfo()
                    naviHelper.getRouteStateInfo()
                    naviHelper.getTBTInfo()

                    Log.d("QueueGuideNavi", "정보 조회 요청 완료")

                } catch (e: Exception) {
                    Log.e("QueueGuideNavi", "정보 조회 오류", e)

                }
            },

            onFailed = { error ->
                Log.e("QueueGuideNavi", "연결 실패", error)

            }
        )
    }

    override fun onDestroy() {
        if (::naviHelper.isInitialized) {
            if (listenerRegistered) {
                naviHelper.removeListener(naviEventListener)
            }
            naviHelper.release()
        }
        locationManager?.removeUpdates(speedListener)
        super.onDestroy()
    }
}



private data class WarningCandidate(
    val label: String,
    val tbtDistance: Double,
    val segment: CongestionSegment,
    val relation: String
)

private data class ActiveWarning(
    val key: String,
    val label: String,
    val congestionStartAtAlert: Double,
    val congestionEndAtAlert: Double,
    val tbtDistanceAtAlert: Double,
    val destinationRemainAtAlert: Double
)

data class CongestionSegment(
    val start: Double,
    val length: Double
)

data class CongestionMatch(
    val segment: CongestionSegment,
    val relation: String,
    val gap: Double
)

fun findCongestionNearTbt(
    tbtDistance: Double,
    sections: List<CongestionSegment>
): List<CongestionMatch> {
    return sections.mapNotNull { section ->
        val start = section.start
        val end = start + section.length

        if (section.length <= 0) return@mapNotNull null

        when {
            // 분기점 이전의 정체 구간
            end < tbtDistance && tbtDistance - end <= 300 ->
                CongestionMatch(
                    section, "BEFORE", tbtDistance - end
                )

            // 정체 구간에 분기점이 포함됨
            start <= tbtDistance && tbtDistance <= end ->
                CongestionMatch(
                    section, "OVERLAP", 0.0
                )

            else -> null
        }
    }
}


fun calculateWarningDistance(speedKmh: Double): Double {
    val speedMps = speedKmh.coerceAtLeast(0.0) / 3.6
    val distance = 300.0 + speedMps * 24.0

    return distance.coerceIn(400.0, 1200.0)
}


fun shouldTriggerWarning(
    congestionStart: Double,
    warningDistance: Double
): Boolean {
    return congestionStart > 0.0 &&
            congestionStart <= warningDistance
}


fun shouldEmitWarning(
    key: String,
    congestionStart: Double,
    warningDistance: Double,
    warnedKeys: MutableSet<String>
): Boolean {
    if (!shouldTriggerWarning(congestionStart, warningDistance)) {
        return false
    }

    return warnedKeys.add(key)
}