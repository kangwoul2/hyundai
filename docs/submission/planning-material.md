# 기획서 작성 재료 (접수양식 항목별)

공식 접수양식을 채울 때 아래 문장과 근거를 복사해 쓴다. **검증된 사실과 가설을 구분해** 적었다.

## 1. 서비스 개요

BabyRelay는 아기와 함께 이동하는 보호자가 필요한 정차를 계획하고, 계획한 장소를 차량 내비게이션 경유지로 연결하며, 목적지 도착 후 돌봄 기록을 다음 보호자에게 인계 카드로 전달하는 차량용 앱이다.

## 2. 문제와 대상 (가설)

생후 3~12개월 아이와 1~3시간 이동하는 보호자. 휴식 장소 탐색, 내비 재등록, 돌봄 기록 전달이 분리되어 번거롭다. *사용자 조사·시장 수치는 없음 — 쓰지 말 것.*

## 3. 차량특화성

- 내비 경유지 등록·취소·조회와 도착 이벤트를 앱 상태로 연결(휴대폰 앱은 불가). **실제 Pleos 에뮬레이터에서 경로 요청·내비 표시·취소 콜백 확인**, 도착 콜백은 Replay 검증.
- 주행 제한(`CarUxRestrictions`)에 따라 조작 잠금. 도착 이벤트를 정차 증거로 쓰지 않음.
- 서버 없이 기기 내 저장.

## 4. Pleos SDK 활용 (접수양식 "개발 계획" 용)

| SDK | 사용 | 검증 |
| --- | --- | --- |
| NaviHelper | 초기화, 리스너, `requestRouteWithWaypoints`, `getWaypointInfo`, `getDestinationInfo`, `getCurrentLocationInfo`, 콜백(`onRouteStarted`, `onRouteEnded`, `onRouteCancelled`, `onWayPointInfo`, `onDrivingInfo`, `onNavigationStatus`, `onWayPointArrived`, `onDestinationArrived`) | 도착 2종 제외 에뮬레이터 확인 |
| (AAOS) CarUxRestrictions | 주행 제한 기반 UX 잠금 | AAOS 에뮬레이터 확인 |
| Vehicle/FusedLocation/TTS/STT | 미사용 | — |

## 5. 구현 범위와 일정

구현 완료: 출발 계획, 규칙 기반 추천(근거 문장), 경유지 등록, 이동·휴식·도착 상태 머신, 돌봄 기록, 인계 카드, 재시작 복구, Replay 시연 도구, 단위 72/계측 4 테스트. 남은 것: 도착 콜백 실검증, 실제 시설 데이터, 선택 기능(QR/TTS 등), 사용성 시험.

## 6. 추천 알고리즘 (설명 가능)

필수 시설 조건 필터 → 출발→도착 직선 기준 위치·희망 시간 적합(평균 60km/h 가정, 문구에 명시) → 출처·확인일 신뢰도 → 상위 2건 → 근거 문장. 실제 도로 ETA/우회 시간은 만들어 내지 않는다.

## 7. 사업성 (가설 — 검증 필요)

- 방향: 차량 OEM 앱마켓 채널, 육아시설 데이터 제휴. 수치(시장 규모, 전환율)는 근거 없이 쓰지 않는다.
- 데이터 신뢰도(이용 기록의 환류)는 구현하지 않았다.

## 8. 위험과 한계 (솔직하게 쓸 것)

시설 데이터가 합성 샘플, 도착 콜백 실검증 미완료, 에뮬레이터만 검증, 소프트웨어 그래픽 환경, 사용성 시험 없음.

## 9. 첨부 가능 자료

README, [sdk-integration.md](../development/sdk-integration.md), [test-results.md](../testing/test-results.md), 스크린샷, 영상 초안, [evidence-manifest.md](../demo/evidence-manifest.md).
