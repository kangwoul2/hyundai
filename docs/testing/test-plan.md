# 테스트 계획

원칙: 실행하지 않은 테스트를 성공으로 쓰지 않는다. 결과는 `test-results.md`와 `logs/`에만 기록한다.

## 계층

| 계층 | 도구 | 대상 | 위치 |
| --- | --- | --- | --- |
| 단위(JVM) | JUnit4, kotlinx-coroutines-test | 상태 머신, 추천, 인계 카드, 주행 제한 정책, 컨트롤러(+Replay), 데이터 품질, 스냅샷 파싱 | `app/src/test/` |
| 계측(에뮬레이터) | AndroidX Test | Room 저장·복구·삭제, 번들 자산, AAOS CarUxRestrictions 읽기 | `app/src/androidTest/` |
| 수동·스크립트 시나리오 | `tools/demo_capture.py` | REPLAY 전 과정, 주행 제한 잠금, 라이트/다크, 해상도별 화면 | `docs/design/screenshots/` |
| SDK 연동(G1) | Pleos Connect 에뮬레이터 | NaviHelper 연결, 경로 요청, 콜백 | `docs/development/sdk-integration.md` |

## 필수 테스트 항목 대응표

| 요구 | 테스트 |
| --- | --- |
| 상태 머신 정상 전이 | `TripStateMachineTest` 정상 전이 / 경유지 없는 여정 |
| 잘못된 전이 무시·거부 | `잘못된 전이…`, `출발 전 도착 이벤트는 거부…` |
| 중복 이벤트 | `중복 경유지/목적지 도착 이벤트…`, 컨트롤러 `중복 도착 이벤트는 한 번만…` |
| 경유지 없는 여정 | 상태 머신 + 컨트롤러 `경유지 없이 출발해…` |
| 추천 조건 필터 | `필수 시설 조건을 모두 갖춘 시설만…` |
| 후보 정렬 결정성 | `점수가 같으면 id 순으로 결정적이다`(입력 순서 뒤집기) |
| 추천 이유 정확성 | `추천 이유는 실제 계산값과 일치한다`(km·분·가정 문구, 우회 시간 수치 없음) |
| 데이터 누락 | 빈 목록·미상 시설·데이터 없는 지역·파일 읽기 실패·깨진 JSON |
| 외부 SDK 오류 | `내비 요청 실패…`, `연결되지 않은 상태…`, `내비 오류 이벤트…`, 확인 이벤트 없음(미확인) |
| Replay 재생 | 컨트롤러 전체 여정, `Replay 시나리오는…결정적`, 7개 시나리오 |
| 앱 재시작 복구 | 단위 `앱 재시작 후…복구`, 계측 `roomStore_saveLoadActive_andRestartRecovery` |
| 운전 중 입력 제한 | `주행 제한 중에는…거부`, `상태를 알 수 없으면…`, `도착 후 전달 물품…` |
| 인계 카드 생성 | `HandoffAndUxTest` 6건 + 컨트롤러 전체 여정 |
| 개인정보 불필요 저장 | `저장된 여정에는 사용자 현재 위치도…없다`(JSON 검사), 카드 텍스트에 이름·좌표 없음 |

## 실행 방법

[setup-windows.md](../development/setup-windows.md)의 명령을 사용한다.

## 한계

- `PleosNaviAdapter`는 Android `Context`와 Pleos 서비스가 필요해 JVM에서 시험할 수 없다. 에뮬레이터 결과로만 판단한다.
- 에뮬레이터의 가짜 신호(Virtual Console, VHAL 주입)는 실차 성능이나 사용성을 증명하지 않는다.
- 사용성 시험(실제 보호자 대상)은 하지 않았다. 사용자 문제 가설은 검증되지 않았다.
- 접근성, 성능, 배터리, 장시간 안정성 시험은 하지 않았다.
