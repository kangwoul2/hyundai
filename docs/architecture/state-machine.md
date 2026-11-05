# 여정 상태 머신

구현: [TripStateMachine.kt](../../app/src/main/java/com/babyrelay/app/domain/TripStateMachine.kt) — 순수 함수 `reduce(session, event, now)`.
테스트: [TripStateMachineTest.kt](../../app/src/test/java/com/babyrelay/app/domain/TripStateMachineTest.kt) (14건).

> 이 상태는 **내비가 알려준 이동 사실**을 나타낸다. 화면 조작 가능 여부는 별도의 `UxPolicy`(플랫폼 `CarUxRestrictions`)가 결정한다. 두 개념을 하나의 조건으로 합치지 않는다.

## 상태와 전이

```text
PLANNING --StartTrip--> DRIVING --WaypointArrived--> STOPPED --Resume--> DRIVING
   |                       |                              |
   | CancelPlanning        | DestinationArrived           | DestinationArrived
   v                       v                              v
 CLOSED                 ARRIVED <-------------------------+
                           | Close(noHandoff?)
                           v
                         CLOSED
(DRIVING/STOPPED --RouteCancelled--> CLOSED[ROUTE_CANCELLED])
```

## 이벤트 처리 규칙

| 이벤트 | PLANNING | DRIVING | STOPPED | ARRIVED | CLOSED |
| --- | --- | --- | --- | --- | --- |
| StartTrip | → DRIVING | 무시(이미 출발) | 무시 | 거부 | 거부 |
| WaypointArrived(index) | 거부 | → STOPPED | 현재 정차 지점 갱신 | 거부 | 거부 |
| WaypointArrived(같은 index 재수신) | 무시 | 무시 | 무시 | 무시 | 무시 |
| Resume | 거부 | 무시 | → DRIVING | 거부 | 거부 |
| DestinationArrived | 거부 | → ARRIVED | → ARRIVED | 무시(중복) | 무시 |
| RouteCancelled | 무시 | → CLOSED | → CLOSED | 무시(도착 후 경로 정리) | 무시 |
| CancelPlanning | → CLOSED | 거부 | 거부 | 거부 | 무시 |
| Close | 거부 | 거부 | 거부 | → CLOSED | 무시 |
| RegistrationChanged | 첫 경유지에 적용 | 적용 | 적용 | 무시 | 무시 |

- **Applied / Ignored / Rejected**를 구분해 반환하고, 모두 `log`에 `(시각, 이벤트, 출처, 결과)`로 남긴다(최대 60건).
- 자동 이벤트(`NAVI_SDK`, `REPLAY`)와 수동 조작(`USER`)은 `EventSource.automatic`으로 구분한다.
- 경유지 없이 `DRIVING → ARRIVED`가 가능하다.

## 앱이 관찰만 하고 확정하지 않는 것

| 항목 | 처리 |
| --- | --- |
| 경유지 등록 성공 | SDK는 요청 후 응답을 주지 않는다. `onRoutingCompleted` 뒤 `getWaypointInfo`로 조회해 일치할 때 `CONFIRMED`. 8초 내 확인 못하면 `UNCONFIRMED`로 바꾸고 장소 이름·주소를 보여준다 |
| 재출발 | 내비에 "재출발" 이벤트가 문서에서 확인되지 않아 수동 `Resume`만 지원(`UNKNOWN`) |
| 앱이 요청하지 않은 `onRouteStarted` | 출발로 간주하지 않는다 |

## 앱 재시작

`TripStore.loadActive()`가 종료되지 않은 가장 최근 여정을 복구한다. 복구 후에도 같은 규칙으로 이벤트를 처리한다(계측 테스트 `roomStore_saveLoadActive_andRestartRecovery`, 단위 테스트 `앱 재시작 후 진행 중인 여정이 복구된다`).
