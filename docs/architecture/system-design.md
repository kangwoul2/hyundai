# 시스템 설계

## 구성

```text
                ┌──────────────────────── UI (Jetpack Compose) ────────────────────────┐
                │ Root(상단 상태·배너·개발 패널) / Plan / Trip / Stop / Arrival / Closed │
                └───────────────▲──────────────────────────────┬────────────────────────┘
                     UiModel(StateFlow)                       조작(run{...})
                ┌───────────────┴──────────────────────────────▼────────────────────────┐
                │ MainViewModel  ── PLEOS 시도 → 실패 시 REPLAY 전환(사유 표시)         │
                └───────────────▲──────────────────────────────┬────────────────────────┘
                                │                              │
          ┌─────────────────────┴────────────┐     ┌───────────▼───────────────────────┐
          │ TripController (JVM 테스트 가능) │◀────│ NavigationGateway (인터페이스)    │
          │  · 내비 이벤트 → 상태 머신       │     │  ├ PleosNaviAdapter  → NaviHelper │
          │  · UxPolicy로 조작 가능 여부     │     │  └ ReplayNaviAdapter → 재생 이벤트│
          │  · 추천/인계 카드/저장            │     └───────────────────────────────────┘
          └──┬───────────┬───────────┬───────┘
             │           │           │
   TripStateMachine  StopRecommender  HandoffCardBuilder     UxRestrictionSource
   (순수 함수)       (규칙 기반)      (순수 함수)           ├ AaosUxRestrictionSource (CarUxRestrictions, 리플렉션)
                                                             ├ SimulatedUxSource (REPLAY 시뮬레이션)
   FacilityRepository (assets JSON)   TripStore (Room)        └ SwitchableUxSource (REPLAY에서 플랫폼 값 선택)
```

패키지: `com.babyrelay.app` — `domain/`(SDK 비의존) · `data/navigation|facility|local|platform` · `app/`(컨트롤러·컨테이너) · `ui/`.

## 설계 원칙

1. **SDK 타입이 도메인으로 새지 않는다.** SDK 응답은 어댑터에서 `NaviEvent`(프로젝트 내부 표현)로 변환한다. `NavigationGateway`, `TripController`, `ReplayDirector` 같은 이름은 내부용이며 Pleos 공식 명칭이 아니다.
2. **PLEOS와 REPLAY는 같은 `TripController`·상태 머신·추천 로직을 쓴다.** 차이는 `NavigationGateway` 구현뿐이다.
3. **모드를 항상 화면에 표시한다.** 상단 태그 `PLEOS · 내비 연결됨` 또는 `REPLAY · 시뮬레이션 (실제 연동 아님)`. 인계 카드와 돌봄 기록에도 기록 당시 모드가 남는다.
4. **내비는 요청-응답이 아니라 요청-이벤트다.** `requestRouteWithWaypoints`는 반환값이 없다. 성공은 `onRoutingCompleted` → `getWaypointInfo` → `onWayPointInfo`로 확인하고, 확인되지 않으면 `UNCONFIRMED`로 표시한다.
5. **저장은 최신 상태 우선(conflated channel)으로 순서 보장.**

## 의존성

| 구성요소 | 좌표/버전 | 근거 |
| --- | --- | --- |
| Pleos NaviHelper | `ai.pleos.playground:NaviHelper:3.0.5` (Maven: nexus-playground.pleos.ai) | 공식 문서 importing-sdk |
| Kotlin / AGP / Gradle | 2.0.21 / 8.5.2 / 8.9 | 공식 조건(Gradle 8.0 이상, API 28 이상) |
| Compose BOM, Room, Coroutines, kotlinx-serialization | 2024.09.03, 2.6.1, 1.8.1, 1.6.3 | |

## 데이터 흐름

```text
[내비 이벤트] → NaviEvent → TripController.onNavi → TripStateMachine.reduce → ControllerState(StateFlow) → UI
[사용자 조작] → UxPolicy(주행 제한) 통과 시에만 → TripController → 상태/기록 → Room(기기 내부)
```

서버·외부 전송 없음. 위치(`CurrentLocation`)는 추천 계산에만 메모리로 쓰고 저장·로그하지 않는다.
