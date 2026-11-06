# 테스트 결과

> 이 문서는 **실제로 실행한 결과만** 기록한다. 실행하지 않은 항목은 "미실행"으로 적는다.
> 마지막 갱신: 2026-11-05. 환경: Windows 11, JDK 17.0.21, Gradle 8.9, AGP 8.5.2.

## 1. JVM 단위 테스트

명령: `./gradlew.bat :app:testDebugUnitTest`

| 항목 | 결과 |
| --- | --- |
| 총 | **72건, 실패 0, 오류 0, 건너뜀 0** |
| 구성 | TripStateMachineTest 14 · StopRecommenderTest 14 · HandoffAndUxTest 9 · FacilityDataTest 7 · TripControllerTest 28(통합: Replay 전체 여정 등) |
| 로그 | [logs/unit-test-run-1.txt](logs/unit-test-run-1.txt)(70건 시점), 이후 2건 추가 후 재실행 통과(72건) |

실패 이력(숨기지 않음):
1. 최초 실행 70건 중 4건 실패 → 원인은 (a) 테스트의 `advanceUntilIdle()`이 `backgroundScope` 작업(저장 코루틴)을 실행하지 않는 하니스 특성 3건, (b) 개인정보 테스트가 `"origin"` 키워드를 검사했는데 시설의 `origin`(데이터 출처) 필드와 이름이 충돌한 테스트 오류 1건. 앱 코드 결함이 아니라 테스트 결함이었고 테스트를 삭제·비활성화하지 않고 수정했다(`runCurrent()` 사용, 사용자 위치 필드명으로 검사).
2. 실제 Pleos 에뮬레이터 관찰(아래 4절) 후 회귀 테스트 `실제 Pleos처럼 RoutingCompleted 없이 RouteStarted만 와도…` 1건 추가, 도착 후 전달 물품 테스트 1건 추가.

## 2. 계측 테스트 (Google AAOS API 34 에뮬레이터, Pleos 이미지 아님)

명령: `./gradlew.bat :app:connectedDebugAndroidTest` — **4건 통과 / 실패 0**

| 테스트 | 확인 |
| --- | --- |
| `roomStore_saveLoadActive_andRestartRecovery` | DB를 닫았다 다시 열어도 진행 중 여정 복구 |
| `roomStore_closedTripsAreNotActive_andPurged` | 종료 여정은 활성 목록 제외, 기간 지난 종료 여정 삭제 |
| `bundledSnapshot_loadsFromAssets` | 번들 SAMPLE 스냅샷 로드 |
| `aaosUxRestrictions_readsPlatformValue` | `CarUxRestrictions`를 PLATFORM 출처로 읽음 |

### AAOS CarUxRestrictions 값 변화 (VHAL 주입, 에뮬레이터 신호)

`adb shell cmd car_service inject-vhal-event` 로 기어/속도/주차 브레이크를 주입하고 앱의 `AaosUxRestrictionSource`로 읽은 `isRequiresDistractionOptimization`:

| 주입 상태 | 읽은 값 |
| --- | --- |
| 기어 D, 15 m/s, 주차 브레이크 해제 | **true** |
| 기어 P, 0 m/s, 주차 브레이크 | **false** |

앱 UI에서도 같은 값으로 잠금/해제 확인(스크린샷 `07b-platform-driving-dark.png`, `07c-platform-parked-dark.png`). 에뮬레이터 가짜 신호이며 실차 검증이 아니다.

### 계측 테스트 — Pleos Connect 에뮬레이터에서도 실행

같은 4건을 **Pleos Connect Emulator v2.1.0**(`pleos21`)에서 실행: 4건 중 실패 0, 오류 0. (`aaosUxRestrictions_readsPlatformValue`는 Pleos 이미지에서도 PLATFORM 값을 읽음 — 주차 상태 값 false. 주행 중 true 전환은 Pleos 이미지에서 시험하지 않음.) 같은 날 첫 시도는 `connectedDebugAndroidTest`가 실패로 끝났고(테스트 0건 실행, 원인은 로그로 확정하지 못함 — 릴리스 서명 앱이 설치된 상태에서 디버그 서명 앱으로 교체되는 시점의 일시적 문제로 추정) 재실행에서 통과했다.

## 3. 에뮬레이터 시나리오 (스크립트 `tools/demo_capture.py`)

- REPLAY 전체 흐름(계획→추천→등록→이동→휴식 기록→주행 제한 잠금→도착→카드→종료)을 1920×720(다크·라이트), 1920×1080(다크)에서 실행하고 스크린샷 저장. 총 스크린샷은 `docs/design/screenshots/`.
- 릴리스 APK(`apk/한영서_BabyRelay_release.apk`, v2 서명 검증)를 `adb uninstall` 후 설치하고 실행 확인.

## 4. 실제 Pleos Connect 에뮬레이터 (G1)

`docs/development/sdk-integration.md` 참조.

## 5. 미실행·미검증

- 세로(포트레이트)·분할 창 화면
- 사용성 시험(실제 보호자), 성능·장시간 안정성
- 실제 차량(실차) 검증
- 실제 공공데이터 기반 추천 품질
