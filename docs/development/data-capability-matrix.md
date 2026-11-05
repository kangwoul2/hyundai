# Pleos 차량 데이터 접근성 및 검증표

**현재 상태:** Claude 원문에서 조사된 범위의 요약이며, 이 저장소에서는 SDK 실제 호출이나 실차·에뮬레이터 테스트를 하지 않았다. '공식 SDK 페이지에 기능이 안내됨'과 '우리 개발 환경에서 동작함'은 다르다.

공식 SDK 개요: https://developers.pleos.ai/playground/discover/resources
공모전 개발 안내: https://appdevchallenge.auto-hmg.io/guide/development

## 활용 후보 목록

| 영역 | Claude 조사에서 언급한 정보 | 제안 활용 | 검증 상태 |
| --- | --- | --- | --- |
| Wiper | 와이퍼 상태 및 스위치 | 강수 간접 신호 | REPORTED, 미테스트 |
| Light | 전조등·안개등 | 시야·기상 맥락 | REPORTED, 미테스트 |
| Seat | 좌석 점유·벨트 | 승하차·동승자 경험 | REPORTED, 미테스트 |
| EvBattery | SOC·충전 상태·전비 | 개인화 에너지 추정 | REPORTED, 미테스트 |
| Odometer | 총·트립 주행거리 | 장기 주행 패턴 | REPORTED, 미테스트 |
| HVAC | 설정 온도·풍량·공조 상태 | 쾌적도 기록 | REPORTED, 미테스트 |
| Door / Window | 문·창문 상태 | 주차·승하차 맥락 | REPORTED, 미테스트 |
| Tire | 타이어 관련 상태 | 장기 변화 추적 | 구체적 수치 UNKNOWN |
| Brake | 브레이크 상태 | 정차 상황 추정 | 값의 의미 UNKNOWN |
| NaviHelper | 목적지·주행 연동 | 도착 시점 기능 | **TESTED(2026-11-05, Pleos 에뮬레이터 v2.1.0)**: 연결, 경유지 포함 경로 요청, 경로 취소·경유지 조회 콜백. 도착 콜백은 미검증. [sdk-integration.md](sdk-integration.md) |
| FusedLocation | 위치 관련 기능 | 지역별 정보 결합 | 사용 가능 권한 UNKNOWN |
| TTS/STT | 음성 입출력 | 운전 중 간단 안내 | 제공 상태·권한 UNKNOWN |

## 중요한 제한

- 와이퍼 작동 ≠ 실제 강수량. 운전자 수동 조작·세차·오작동에 의한 오탐 가능성.
- 좌석 점유 ≠ 탑승자의 신원·연령. 개인 구분이나 아동 탐지로 주장하지 말 것.
- Tire 도메인 ≠ 수치형 압력 보장. 메서드·타입과 지원 차종 필요.
- HVAC 상태 ≠ 실외 온습도 제공. 외부 관측 API가 필요할 수 있음.
- Brake 상태 ≠ 정확한 차속 또는 ADAS 사고 경고.
- SDK 문서에 없다는 조사 결과 ≠ 절대 제공되지 않는다는 확정.
- 읽기 권한과 차량 제어 권한은 별도일 수 있다. 미승인 제어 권한을 무단 선언하지 않는다.
- 에뮬레이터 가짜 신호는 실차 성능이나 사용 가능성을 증명하지 않는다.

## 실제 검증 시 채우는 기록표

| 확인 항목 | 기록값 |
| --- | --- |
| Android Studio / OS 버전 | 미확인 |
| Pleos Emulator 버전 / 시스템 이미지 | 미설치·미확인 |
| SDK 의존성 좌표와 버전 | 미확인 |
| 호출한 공식 메서드 이름 | 미확인 |
| 반환 타입과 단위 | 미확인 |
| permission 및 승인 상태 | 미확인 |
| 테스트 대상 차량 또는 에뮬레이터 | 미확인 |
| 데이터 미지원 시 반환 동작 | 미확인 |
| Virtual Console 신호 주입 성공 여부 | 미확인 |
| 재현 가능한 테스트 로그·화면 캡처 | 없음 |

## 후보별 최소 실험

1. RainMesh: 와이퍼 동작 상태를 구독할 수 있는가? 사용 시각과 위치를 동기화할 수 있는가?
2. ParkPulse: 주차/문/창문과 위치 상태를 얻는가? 주차 후 앱 실행 제약은 무엇인가?
3. EcoBudget: SOC·전비의 실제 값과 단위가 무엇이며 변화가 재현되는가?
4. TireMemory: 4륜 압력값 등 장기 측정에 필요한 숫자를 얻을 수 있는가?
5. SeatFlow: 좌석별 점유와 도어 변화 타이밍이 사용 가능한가?
6. CabinAtlas: HVAC 측정값 종류와 단위, 탑승자 입력의 안전한 UI가 가능한가?
7. StopMap: 정확한 이동/정차 및 위치 신호를 지속적으로 얻을 수 있는가?

기록할 때 `OFFICIAL`, `REPORTED`, `TESTED`, `HYPOTHESIS`, `UNKNOWN`을 구분한다.


## 갱신 — 2026-11-05 (NaviHelper 3.0.5 실측)

SDK 배포 소스(공개 Maven `sources.jar`)로 시그니처를 확인하고 Pleos Connect Emulator v2.1.0(API 34)에서 실행했다. 표는 [sdk-integration.md](sdk-integration.md)에 있다. `TESTED`로 올린 것은 그 문서의 TESTED 행뿐이다. 나머지 신호(Vehicle SDK 등)는 이번 MVP에서 사용하지 않았으므로 상태가 바뀌지 않았다.
