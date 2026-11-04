
package com.example.queueguide

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.navi.data.CurrentLocationInfo

import com.example.queueguide.ui.theme.QueueGuideTheme

import ai.pleos.playground.navi.data.DestinationInfo

import ai.pleos.playground.navi.data.RouteStateInfo

class MainActivity : ComponentActivity() {

    private lateinit var naviHelper: NaviHelper
    private var listenerRegistered = false

    private val connectionStatus = mutableStateOf("연결 대기 중")
    private val locationStatus = mutableStateOf("위치 정보 없음")

    // 위치 정보 수신 리스너
    private val naviEventListener = object : NaviHelperEventListener {

        override fun onCurrentLocationInfo(
            currentLocationInfo: CurrentLocationInfo
        ) {
            Log.d(
                "QueueGuideNavi",
                "위치 정보 수신: $currentLocationInfo"
            )

            runOnUiThread {
                locationStatus.value = "위치 정보 수신 성공!"
            }
        }

        override fun onDestinationInfo(
            destinationInfo: DestinationInfo
        ) {
            Log.d(
                "QueueGuideNavi",
                "목적지 정보 수신: $destinationInfo"
            )
        }

        override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) {

            val sections = routeStateInfo
                .trafficStatus
                ?.congestionItemList
                .orEmpty()

            Log.d(
                "QueueGuideTraffic",
                "전체 정체 구간 수: ${sections.size}"
            )

            sections
                .sortedBy { it.offset.toDouble() }
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
        }



    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            QueueGuideTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    Text("QueueGuide")

                    Spacer(modifier = Modifier.height(16.dp))

                    Text("NaviHelper 연결 상태")
                    Text(connectionStatus.value)

                    Spacer(modifier = Modifier.height(24.dp))

                    Text("현재 차량 위치")
                    Text(locationStatus.value)
                }
            }
        }

        naviHelper = NaviHelper(applicationContext)

        connectionStatus.value = "연결 시도 중..."

        naviHelper.initialize(
            onConnected = {
                Log.d("QueueGuideNavi", "NaviHelper 연결 성공")

                runOnUiThread {
                    connectionStatus.value = "NaviHelper 연결 성공!"
                    locationStatus.value = "위치 조회 요청 중..."
                }

                try {
                    // 연결 성공 후 리스너 등록
                    naviHelper.addListener(naviEventListener)
                    listenerRegistered = true

                    // 현재 차량 위치 조회
                    naviHelper.getCurrentLocationInfo()

                    naviHelper.getDestinationInfo()

                    naviHelper.getRouteStateInfo()

                    Log.d(
                        "QueueGuideNavi",
                        "getCurrentLocationInfo 호출 완료"
                    )

                } catch (e: Exception) {
                    Log.e("QueueGuideNavi", "위치 조회 요청 오류", e)

                    runOnUiThread {
                        locationStatus.value =
                            "위치 조회 요청 오류: ${e.message}"
                    }
                }
            },

            onFailed = { throwable ->
                Log.e("QueueGuideNavi", "연결 실패", throwable)

                runOnUiThread {
                    connectionStatus.value =
                        "연결 실패: ${throwable.message}"
                }
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
        super.onDestroy()
    }
}