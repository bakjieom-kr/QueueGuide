package com.example.queueguide

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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


// 시연 후 제출용 APK에서는 false로 변경하세요.
private const val ENABLE_DEMO = true

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

    // 실제 내비게이션 상태를 변경하지 않는 독립 시연 상태
    private val demoStep = mutableStateOf(-1)
    private val demoWarningActive = mutableStateOf(false)
    private val demoStatus = mutableStateOf("")


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
                hideWarning()
                Log.d("QueueGuideWarn", "ROUTE_RESET: 경로 데이터 및 경고 이력 초기화")
                return
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
                hideWarning()
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

    private fun showWarning(active: ActiveWarning, destRemain: Double) {
        val message = warningText(active, destRemain)
        runOnUiThread {
            warningActive.value = true
            guideStatus.value = message
        }
    }

    // 시연: 일반 → 700m 접근 → 정체 진입 → 정차 → 통과 → 시연 종료
    // 실제 NaviHelper/GPS 데이터 및 실제 경고 이력에는 영향을 주지 않는다.
    private fun advanceDemo() {
        val next = if (demoStep.value >= 4) -1 else demoStep.value + 1
        demoStep.value = next

        val mockWarning = ActiveWarning(
            key = "DEMO_LEFT@1100",
            label = "TURN_LEFT (판교역 방면)",
            congestionStartAtAlert = 700.0,
            congestionEndAtAlert = 900.0,
            tbtDistanceAtAlert = 1100.0,
            destinationRemainAtAlert = 2000.0
        )

        val labels = listOf(
            "일반: 정체 1,000m 전 (경고 기준 700m)",
            "경고: 정체 시작까지 700m",
            "정체 진입: 시작점 도달",
            "정차: 위치 변화 없음",
            "통과: 정체 종료점 1m 지나감"
        )

        val remain = when (next) {
            1 -> 2000.0  // 경고 시작까지 700m
            2, 3 -> 1300.0 // 정체 시작점 도달 및 정차
            4 -> 1099.0  // 901m 이동, 정체 종료점(900m)을 통과
            else -> 2000.0
        }

        val passed = hasPassedCongestion(mockWarning, remain)
        val visible = next in 1..3 && !passed
        demoWarningActive.value = visible
        demoStatus.value = if (visible) warningText(mockWarning, remain) else ""

        Log.d(
            "QueueGuideDemo",
            "STEP=$next, phase=${if (next < 0) "시연 종료" else labels[next]}, " +
                    "UI=${if (visible) "ON" else "OFF"}, passed=$passed"
        )
    }

    private fun hideWarning() {
        runOnUiThread {
            warningActive.value = false
            guideStatus.value = ""
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
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    Text("QueueGuide")

                    // 에뮬레이터 검증용 버튼. 배포 전 ENABLE_DEMO=false 처리.
                    if (ENABLE_DEMO) {
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { advanceDemo() }) {
                            Text(
                                when (demoStep.value) {
                                    -1 -> "시연 시작"
                                    4 -> "시연 종료"
                                    else -> "다음 단계"
                                }
                            )
                        }
                        if (demoStep.value >= 0) {
                            val names = listOf("일반", "접근 경고", "정체 진입", "정차", "통과")
                            Text("테스트 모드 · ${names[demoStep.value]}")
                        }
                    }

                    // 실제 경고 또는 독립 시연 중 하나만 표시
                    val demoRunning = ENABLE_DEMO && demoStep.value >= 0
                    val visible = if (demoRunning) demoWarningActive.value else warningActive.value
                    val message = if (demoRunning) demoStatus.value else guideStatus.value
                    if (visible) {
                        Spacer(Modifier.height(24.dp))
                        Text(message)
                    }

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

