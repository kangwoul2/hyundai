# Pleos NaviHelper 연동 검증 (G1)

검증일 2026-11-05. **TESTED는 아래 환경에서 실제로 실행해 로그나 화면으로 확인한 항목만** 표시한다. 에뮬레이터 결과는 실차 성능이나 호환성을 증명하지 않는다.

## 환경 (기록)

| 항목 | 값 |
| --- | --- |
| 호스트 | Windows 11 Enterprise 10.0.26200, i7-11700, RAM 32GB, WHPX |
| 에뮬레이터 이미지 | Pleos Connect Emulator **v2.1.0**, 빌드 `Pleos/crp_avd/crp_avd_x86_64:14/RELEASE.release_connect_v2.1.0` (Android 14, API 34). 파일 `crp_car_emulator.zip` SHA1 `b4f858d5…2acaa11` = 공식 XML 값과 일치 |
| 디스플레이 | 공식 디바이스 정의 "Pleos Connect v2.1.0": 12.8인치, 2560×1440, 160dpi(mdpi) |
| AVD 설정 | RAM 4GB, 콜드 부팅, 소프트웨어 그래픽(`swiftshader_indirect`). (공식 가이드는 하드웨어 GPU를 권장 — 아래 한계 참조) |
| SDK | `ai.pleos.playground:NaviHelper:3.0.5`(공식 문서 기준). 기기의 내비 서비스 버전은 로그에 `getServiceVersion = 3.0.4`로 표시 |
| 권한 | `pleos.car.permission.NAVI_ROUTE`, `NAVI_ROUTE_SEARCH`(Normal 레벨, 매니페스트 선언만으로 동작, 런타임 요청 없음) |
| 앱 | `com.babyrelay.app` v0.1.0-mvp (debug, 그리고 서명된 release APK로 클린 설치 후 연결 확인) |
| 실행 | `adb install` → `am start` → 앱 화면 조작(좌표/텍스트 탭) → `adb logcat -d`. 로그: [g1-pleos-emulator-log.txt](g1-pleos-emulator-log.txt), [g1-pleos-emulator-cancel-log.txt](g1-pleos-emulator-cancel-log.txt) |

## 결과 (요청 항목 5개 + 부가)

| 확인 항목 | 결과 | 증거 / 관찰 |
| --- | --- | --- |
| 1. NaviHelper SDK 연결 | **TESTED 성공** | `initialize(onConnected,onFailed)` → `onServiceConnected`, 앱 상단 `PLEOS · 내비 연결됨`. 스크린샷 `00-release-apk-clean-install.png` |
| 리스너 등록 | **TESTED** | `addListener` 로그 |
| 리스너 해제·`release()` | 구현됨, **미검증**(종료 경로 로그 미확인) | `PleosNaviAdapter.disconnect()` |
| 목적지/현재 위치 조회 | **TESTED(주의)** | `getDestinationInfo`/`getCurrentLocationInfo` 호출됨. 내비에 목적지가 없으면 `onDestinationInfo`가 **빈 이름·빈 좌표로 호출**됨(앱에서 무시하도록 수정). 콜드 부팅 직후에는 현재 위치 콜백이 없는 경우가 있었고(앱은 "목적지 근처 후보" 폴백 안내), 이후 실행에서는 위치를 받아 추천을 계산함 |
| 2. 경유지 포함 경로 요청 | **TESTED 성공** | `requestRouteWithWaypoints` → 내비 로그 `requestToNaviProvider(RequestRouteWithWaypoints) com.babyrelay.app >> ai.umos.maps.android.navigation.app` |
| 3. 내비 화면의 경유지 등록 결과 | **TESTED 성공** | 내비 경로 카드에 경유지 "샘플 쉼터 07"이 포함된 경로(1시간 25분, 78km, 진행 막대에 경유지 표시). 스크린샷 `02-navi-route-with-waypoint.png`. 내비는 `poiSubId=""`로도 좌표 기준으로 경로를 만들었다 |
| `onRouteStarted` | **TESTED** | 요청 후 **약 12~13초** 뒤 수신(2회 관찰) |
| `onRoutingCompleted` | **수신되지 않음** | 대신 **deprecated `onRouteEnded`**가 호출됨(`onRoutingCompleted` 기본 구현은 호출되지 않음). 어댑터에서 `onRouteEnded`도 같은 이벤트로 처리하도록 수정 |
| `onNavigationStatus` | **TESTED** | `ROUTE_DRIVE`(경로 시작), 취소 후 `FREE_DRIVE` |
| `getWaypointInfo` → `onWayPointInfo` | **TESTED** | `size=1`(경로 시작 후), 경로 취소 후 `size=0`. `onWayPointInfo`는 자동으로 오지 않고 조회해야 함 |
| `onDrivingInfo`(ETA) | **부분 확인** | 앱 이동 화면에 "내비 제공 남은 시간 약 164분"이 표시됨(화면으로 확인). 해당 실행의 로그 파일은 보존하지 못함 |
| `onRouteCancelled` | **TESTED 성공** | 내비의 "안내 종료" → 로그 `onRouteCancelled`, 앱이 `DRIVING → CLOSED(ROUTE_CANCELLED)` 처리. 스크린샷 `03-route-cancelled-handled.png` |
| 4. 경유지 도착 콜백 `onWayPointArrived` | **미검증(막힘)** | 아래 "막힌 이유" |
| 5. 목적지 도착 콜백 `onDestinationArrived` | **미검증(막힘)** | 아래 |
| `onWaypointChanged`, `onTripInfo`(`requestTrip`) | 미검증 / 미사용 | |
| CarUxRestrictions(Pleos 이미지) | **부분 확인** | Pleos 이미지에서 `requiresDistractionOptimization=false`(주차 상태)를 PLATFORM으로 읽음(`BabyRelay.Ux` 로그), 홈 화면이 앱을 `distractionOptimized: true`로 인식. **주행 중 true 전환은 Pleos 이미지에서 미시험** (Google AAOS 이미지에서만 시험) |

### 도착 콜백이 막힌 이유 (시도한 것)

1. `adb emu geo fix <lon> <lat>`로 에뮬레이터 GPS를 경유지 좌표로 이동 → 내비 화면의 현재 위치("수정구 시흥동")와 경로가 **변하지 않음**. 내비는 에뮬레이터 GPS를 쓰지 않는 것으로 보인다.
2. Virtual Console(`adb forward tcp:8088 tcp:8088` → `http://127.0.0.1:8088/`)의 `/vehicle/catalog`를 조회했으나 기능은 도메인 vehicle/motion/adas/convenience/safety/energy/climate/misc(기어, 오도미터, 공조 등 **차량 신호**)뿐이며 위치·내비 주행 시뮬레이션 항목이 없다. 공식 가이드(virtual-console-guide)도 위치 설정·주행 시뮬레이션 방법을 설명하지 않는다.
3. 내비 앱 자체의 시뮬레이션 주행 옵션은 확인하지 못했다.

**해석:** 앱의 도착 처리(`onWayPointArrived` → STOPPED, `onDestinationArrived` → ARRIVED)는 REPLAY와 단위 테스트에서만 검증됐다. 실제 SDK 콜백으로는 **아직 증명하지 못했다.** 이는 차량특화성 주장의 핵심 증거이므로 가장 큰 남은 위험이다(아래).

## SDK 문서와 실제 동작의 차이 (앱에 반영함)

| 문서/소스 | 실제 | 대응 |
| --- | --- | --- |
| `onRoutingCompleted()`로 경로 탐색 완료 통지 | `onRouteEnded()`(deprecated)가 호출됨 | 두 콜백 모두 같은 내부 이벤트로 처리 |
| 요청 후 곧 결과 이벤트 | `onRouteStarted`까지 약 12~13초 | 등록 확인 타임아웃 20초, `RouteStarted` 시 `getWaypointInfo` 조회 |
| 목적지 없을 때 콜백 없음(추정) | 빈 `DestinationInfo` 콜백 | 이름·좌표가 비면 무시 |
| 경유지 반영 자동 통지(추정) | 조회해야 `onWayPointInfo` | `getWaypointInfo()` 호출 |
| 문서 요약 도구가 준 메서드 시그니처 | 불완전 | SDK 배포 `sources.jar`(공개 Maven)로 시그니처를 교차 확인 후 사용 |

## 남은 위험과 대응

| 위험 | 영향 | 대응 |
| --- | --- | --- |
| 도착 콜백 실검증 없음 | 차량특화성의 핵심 근거 약화 | (1) Pleos Playground/지원 채널에서 내비 위치 시뮬레이션 방법 확인 (2) 하드웨어 GPU 환경에서 재시험 (3) 제출 자료에는 "경유지 포함 경로 요청·취소·조회는 실제 에뮬레이터에서 TESTED, 도착 콜백은 REPLAY 검증"이라고 구분해 기재 |
| 에뮬레이터를 소프트웨어 그래픽으로 실행 | 공식 권장(GPU)과 다름. 중간에 `system_server` 재시작 1회 관찰 → 콜드 재부팅으로 복구 | 환경 이슈로 기록. 결과 신뢰도에 영향 가능 |
| 시설 데이터가 SAMPLE | 사업성·사용자 가치 주장에 영향 | `data-sources.md` |

## 실행 방법 (재현)

```powershell
# 1) 이미지 설치(라이선스 동의 필요): crp-sys-img.xml 의 URL에서 zip 다운로드 → system-images\android-34\android-automotive\x86_64 로 압축 해제 → source.properties 의 SystemImage.TagId=android-automotive
# 2) avdmanager create avd -n pleos21 -k "system-images;android-34;android-automotive;x86_64" -d Pleos_Connect_v2_0   (devices.xml 은 이미지에 포함)
# 3) config.ini: hw.ramSize=4096M, fastboot.forceColdBoot=yes
emulator -avd pleos21 -no-snapshot -no-audio
adb install apk\한영서_BabyRelay_release.apk
adb shell am start -n com.babyrelay.app/.MainActivity     # 상단에 PLEOS · 내비 연결됨 확인
adb logcat -s BabyRelay.Pleos:V                             # 콜백 로그
```

앱 조작 팁: Pleos 멀티 윈도에서는 `uiautomator dump`가 포커스된 창만 보여 준다. 앱 창을 한 번 탭한 뒤 `tools/emu.py tap "텍스트"`를 쓴다.
