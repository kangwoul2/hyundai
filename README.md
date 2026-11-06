# BabyRelay

> 아기와의 이동에서 필요한 정차를 계획하고, 차량 내비게이션과 연결하며, 목적지 도착 후 돌봄 정보를 다음 보호자에게 이어주는 차량용 앱.

2026 현대자동차그룹 차량용 앱 개발 공모전 출품 프로젝트(Pleos Connect, Android Automotive). **상태: MVP 구현·테스트 완료(Replay 기준), 실제 Pleos 에뮬레이터 검증은 부분 완료** — 아래 "검증 상태"를 먼저 읽어 주세요.

## 1. 해결하려는 문제

생후 3~12개월 아이와 1~3시간 이동하는 보호자는 (1) 수유·기저귀 교환 가능한 휴식 장소를 찾고, (2) 그 장소를 내비에 다시 등록하고, (3) 이동 중 돌봄 기록을 목적지의 다음 보호자에게 전달하기 어렵다. *(기획 가설이며 사용자 조사 데이터는 아직 없다.)*

## 2. 차량 플랫폼 활용 방식

| 기능 | 사용하는 플랫폼 요소 |
| --- | --- |
| 추천 장소를 내비 경유지로 등록 | Pleos **NaviHelper** `requestRouteWithWaypoints` (실제 에뮬레이터에서 동작 확인) |
| 경유지·목적지 도착을 돌봄 여정 상태에 연결 | `onWayPointArrived`, `onDestinationArrived` (**실제 콜백 검증은 미완료**, Replay로 검증) |
| 경로 취소·경유지 조회·내비 제공 ETA | `onRouteCancelled`, `getWaypointInfo`/`onWayPointInfo`, `onDrivingInfo` (에뮬레이터 확인) |
| 주행 중 조작 제한 | AAOS `CarUxRestrictions`(주행 시 선택·기록·인계 수정 잠금). Google AAOS 이미지에서 주행/주차 값 변화 확인 |

핵심 차별점은 "차량의 이동 상태 변화가 앱의 다음 기능을 결정하는 상태 기반 서비스"다. 도착 이벤트는 **정차의 증거로 쓰지 않으며**, 조작 가능 여부는 플랫폼 주행 제한으로만 결정한다.

## 3. 기술 스택

Kotlin 2.0 · Jetpack Compose · MVVM + StateFlow · Coroutines · Room · kotlinx-serialization · Pleos NaviHelper 3.0.5 · JUnit4 / AndroidX Test. 서버 없음(기기 내 저장). Gradle 8.9, AGP 8.5.2, minSdk 28, targetSdk 34.

## 4. 구현된 기능

- 출발 계획: 목적지(내비에서 가져오기/직접 선택), 다음 휴식 시각, 필요 시설, 추천 후보 최대 2건 + 근거 문장, 경유지 등록(실패 시 장소 이름·주소 안내)
- 이동 중: 상태·다음 휴식 장소·등록 상태·내비 제공 ETA 표시
- 경유지 도착: 상태 전환, 돌봄 기록(수유/기저귀/시설 이용 가능·불가, 시각·출처 저장)
- 목적지 도착: 인계 카드(마지막 수유·기저귀 시각, 전달 물품, 시설 메모), 인계 없이 종료
- 앱 재시작 복구, 중복·잘못된 이벤트 처리, 경로 취소 처리
- 모드 표시: 상단에 항상 `PLEOS · 내비 연결됨` 또는 `REPLAY · 시뮬레이션 (실제 연동 아님)`, 샘플 데이터는 `SAMPLE 데이터`

## 5. 아직 구현하지 않은 것

선택 기능 전부(텍스트 QR 인계 카드, TTS, 준비물 체크리스트 확장, 실제 내비 ETA 기반 후보 비교, 시설 신뢰도 UI). 비범위: 서버, 실시간 보호자 동기화, ISOFIX/카시트 판정, 신생아 감지, 운전 습관 점수, AI 챗봇 등([PRD](docs/requirements/PRD.md), [ADR-001](docs/architecture/ADR-001-mvp-scope.md)).

## 6. 검증 상태 (요약)

| 항목 | 상태 |
| --- | --- |
| JVM 단위 테스트 | **72건 통과 / 0 실패** |
| 계측 테스트(AAOS 에뮬레이터) | **4건 통과** (Room 복구, CarUxRestrictions 읽기 포함) |
| Pleos 에뮬레이터: NaviHelper 연결 | **성공** |
| Pleos 에뮬레이터: 경유지 포함 경로 요청 → 내비 화면 반영 | **성공** (내비 경로에 경유지 표시) |
| Pleos 에뮬레이터: 경로 취소 콜백 | **성공** |
| Pleos 에뮬레이터: 경유지/목적지 **도착 콜백** | **미검증** — 에뮬레이터 내비 위치를 바꿀 방법을 찾지 못함 |
| Release APK 클린 설치(Pleos·AAOS 에뮬레이터) | 성공 |
| 실차, 실제 보호자 사용성 시험 | 하지 않음 |

자세한 근거: [sdk-integration.md](docs/development/sdk-integration.md), [test-results.md](docs/testing/test-results.md), [evidence-manifest.md](docs/demo/evidence-manifest.md). **REPLAY는 실제 Pleos 검증을 대신하지 않는다.**

## 7. 실행 방법

개발 환경: [setup-windows.md](docs/development/setup-windows.md). 요약(PowerShell):

```powershell
$env:JAVA_HOME='<JDK17>'; $env:ANDROID_HOME='<Android SDK>'
./gradlew.bat :app:testDebugUnitTest      # 단위 테스트
./gradlew.bat :app:assembleDebug          # 디버그 APK
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

Pleos SDK 의존성은 공개 Maven(`nexus-playground.pleos.ai`)에서 내려받는다. SDK 바이너리/소스는 이 저장소에 포함하지 않는다.

## 8. Replay 모드

Pleos 내비 서비스에 연결되지 않으면(예: 일반 AAOS 에뮬레이터) 약 6초 뒤 **자동으로 REPLAY**로 전환하고 이유를 개발 패널에 남긴다. 상단 모드 태그를 누르면 **개발·시연 패널**이 열린다(출시 사용자용 아님): 시나리오 이벤트 재생(출발 계획, 경유지 등록 확인, 경유지 도착, 목적지 도착, 데이터 없음, 중복 이벤트, 경로 취소), 주행 제한 값 출처(시뮬레이션/AAOS 플랫폼), 테마. Pleos 환경에서 REPLAY를 보려면 패널의 "REPLAY 모드로 전환". 장면별 절차: [demo-runbook.md](docs/demo/demo-runbook.md).

## 9. Pleos SDK 연동 검증 결과

[docs/development/sdk-integration.md](docs/development/sdk-integration.md) — 실제 관찰한 SDK와 문서의 차이(예: `onRoutingCompleted` 대신 deprecated `onRouteEnded` 호출, `onRouteStarted`까지 약 12~13초, 목적지 없을 때 빈 `onDestinationInfo`)와 앱의 대응 포함.

## 10. APK 위치

`apk/한영서_BabyRelay_release.apk` (v2 서명, SHA-256은 [evidence-manifest.md](docs/demo/evidence-manifest.md)). 개인 참가이므로 공식 형식 `팀명_앱이름_release.apk`의 팀명 자리에 성명을 썼다. 서명 키는 저장소에 없다.

## 11. 시연 영상

[docs/demo/video/](docs/demo/video/): `scene-pleos-navi-real.mp4`(실제 Pleos 에뮬레이터), `scene-replay-flow.mp4`(REPLAY), `babyrelay-demo-A-draft.mp4`(영상 A 초안 약 98초). 각 장면의 출처는 자막과 [shot-list.md](docs/demo/shot-list.md)에 표기. SNS 업로드는 하지 않았다.

## 12. 데이터 출처

**시설 데이터는 합성 SAMPLE 14건**이다(실제 시설 아님). 실제 공공데이터는 포털 로그인/인증키가 필요해 확보하지 못했다. [data-sources.md](docs/development/data-sources.md)에 품질 지표와 교체 절차.

## 13. 주행 안전 및 개인정보

- 주행 제한(`CarUxRestrictions`)이 true이거나 알 수 없으면 시설 선택·조건 변경·기록 입력·인계 수정을 잠근다. 자유 텍스트 입력이 없다.
- 저장: 목적지·경유지 이름, 돌봄 기록(종류·시각·출처·모드), 전달 물품. 저장하지 않음: 아이 이름·사진·연락처, 현재 위치·이동 경로 이력. 서버 전송 없음, 종료된 여정은 7일 후 삭제.

## 14. 디자인

[pleos-design-spec.md](docs/design/pleos-design-spec.md). **공식 색·크기 토큰 값은 공식 페이지에서 텍스트로 확인하지 못해 자체 임시 토큰을 사용했다.** Hyundai 로고·상표 미사용. 스크린샷: `docs/design/screenshots/`.

## 문서 지도

요구사항 [PRD](docs/requirements/PRD.md) · 설계 [system-design](docs/architecture/system-design.md), [state-machine](docs/architecture/state-machine.md) · 테스트 [test-plan](docs/testing/test-plan.md), [test-results](docs/testing/test-results.md) · 제출 [checklist](docs/submission/submission-checklist.md), [기획서 자료](docs/submission/planning-material.md) · 기획 단계 자료(원문 보존): [docs/research/](docs/research/), [competition/rules.md](docs/competition/rules.md), [ideas](docs/ideas/idea-pool.md), [decision-log](docs/process/decision-log.md)

작업 지침(AI 협업): [AGENTS.md](AGENTS.md)
