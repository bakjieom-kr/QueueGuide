# QueueGuide (큐가이드)

> **도심 교차로 등 경로상 정체 시작점을 미리 안내하여 운전자의 안전한 차로 선택을 돕는 차량용 안내 앱**

- **팀명:** QUIDE (큐이드)
- **앱 이름:** QueueGuide (큐가이드)
- **개발 플랫폼:** Android Automotive · Pleos Connect
- **구현 언어 / UI:** Kotlin · Jetpack Compose

## 1. 프로젝트 소개

QueueGuide는 내비게이션 경로의 정체 구간 정보와 회전 안내 지점(TBT)의 위치 관계를 비교하여 정체 시작점에 접근 중임을 사전에 알려주는 앱입니다. 특히 **도심 교차로 우회전 대기행렬이 교차로보다 앞쪽까지 이어지는 상황**을 대표 사용 사례로 제시합니다.

안내 화면에는 정체 시작점까지의 거리와 관련 회전 지점까지의 거리를 함께 표시합니다. 정체 정보는 운전자가 주변 상황을 미리 확인하도록 돕는 보조 정보이며, 특정 차로 진입을 강제하거나 안전을 보증하지 않습니다.

## 2. 주요 기능

- **경로상 정체 구간 탐지:** NaviHelper가 제공하는 정체 구간 정보와 TBT 안내 지점을 거리 기준으로 비교합니다.
- **속도 기반 사전 경고:** 차량 속도에 따라 경고 거리를 다르게 계산합니다. 초기 실험 파라미터는 `300m + 속도(m/s) × 24초`이며, 계산값을 400~1,200m 범위로 제한합니다.
- **안내 상태 관리:** 정체 시작점 접근 → 구간 진입 → 정차 → 구간 통과 상황에 따른 안내를 표시합니다.
- **차량용 시연 UI:** 정적 지도 배경 이미지에 가상 주행 경로, 차량 위치 및 정체 대기행렬을 시각화하여 도심 교차로 우회전 상황에서의 감속·정차·재출발 과정을 시연합니다.

**중요:** 현재 제출용 APK는 **시연 모드**로 실행됩니다. 시연 지도, 차량 이동, 속도, 정체 위치 및 차로별 대기행렬은 가상 데이터입니다. NaviHelper 연동 코드와 데이터 수신·매칭 기능은 프로젝트에 포함되어 있으나, 제출용 시연 화면을 실제 차로별 정체 감지 결과로 해석해서는 안 됩니다.

## 3. APK 위치 및 실행 방법

- **APK:** [`apk/QUIDE_QueueGuide_release.apk`](apk/QUIDE_QueueGuide_release.apk)
- **패키지명:** `com.example.queueguide`

1. Pleos Connect Android Automotive 에뮬레이터를 실행합니다.
2. APK 파일을 에뮬레이터 화면으로 드래그해 설치하거나 `adb install apk/QUIDE_QueueGuide_release.apk` 명령으로 설치합니다.
3. 에뮬레이터 앱 목록에서 **QueueGuide**를 실행합니다.
4. 화면에서 일반 주행 → 정체 접근 → 정체 진입 → 정차 → 재출발 → 정체 구간 통과 흐름을 확인합니다.
5. 시연 종료 화면의 **시연 다시 보기** 버튼으로 반복 실행할 수 있습니다.

## 4. 테스트 환경 및 설정

| 항목 | 내용 |
|---|---|
| 차량용 개발 환경 | Pleos Connect Android Automotive 에뮬레이터 |
| Android API | API 34 |
| 에뮬레이터 메모리 | 4GB 구성에서 개발 및 테스트 |
| 화면 방향 | 가로(차량용 디스플레이) |
| 화면 해상도 | 2560x1440 |
| 내비게이션 SDK | NaviHelper v3.0.5 |
| 개발 도구 | Android Studio |
| 제출 APK 유형 | 서명된 Release APK |
| 시연 데이터 | 앱 내 가상 경로·정체 데이터 |

## 5. 소스 코드 및 구현 범위

- `app/src/main/java/com/example/queueguide/MainActivity.kt` — NaviHelper 수신 처리, 정체 매칭, 경고 상태, 시연 제어
- `app/src/main/java/com/example/queueguide/QueueGuideMapDemo.kt` — 가상 도시 교차로 지도 및 시연 UI
- `app/src/main/java/com/example/queueguide/QueueGuideScreen.kt` — 일반 안내 UI와 상태 모델

NaviHelper를 통해 경로·TBT·정체 정보를 수신하고, 정체 구간과 회전 지점의 위치 관계를 판별하는 로직을 구현했습니다. 다만 차로별 정체 행렬 식별은 현재 구현 범위에 포함되지 않으며, 향후 고도화 과제입니다. 또한 실제 도로 환경에서의 경고 정확도, 동적 경고 거리의 안전성, 경로 재탐색 시 동작 일관성에 대한 추가 검증이 필요합니다. 경고 거리의 상수는 검증된 공인 안전 기준이 아닌 MVP용 초기 설정값입니다.

## 6. 제출 안내

- 제출 저장소: 이 GitHub 저장소
- APK: `apk/QUIDE_QueueGuide_release.apk`
- **심사 기준 커밋:** 제출 시점의 최종 commit hash를 접수 메일에 별도로 기재합니다.
