# 개발 환경과 구현 접근 — 아이디어 확정 전 설계

상태: 제안(아직 설치·빌드·실차 테스트하지 않음). 2026-11-05 기준.

## 핵심 설명

이번 공모전에서 실제로 실행하는 프로그램은 **자동차 화면에 표시되는 Android 기반 앱**이다. 따라서 처음에는 Kotlin으로 Pleos Connect에 맞춘 Android 앱을 만드는 것이 가장 직접적인 접근이다. 서버가 반드시 필요한 것은 아니다. 여러 차량의 데이터를 합쳐야 하거나 장기간 수집한 데이터를 분석해야 한다는 요구가 생긴 경우에만 Java/Spring Boot 서버를 추가한다.

사용자는 Java/Spring과 PostgreSQL 기반 개발 경험이 있다. 기존 장점을 활용하되, 공모전 주제가 정해지지 않은 상태에서 백엔드 서버, Kafka, Kubernetes를 먼저 구축하지 않는다.

## 제안 스택

| 영역 | 우선 선택 | 도입 기준 |
| --- | --- | --- |
| 개발 PC | Windows, Android Studio | 앱 제작 및 에뮬레이터 |
| 차량용 앱 | Kotlin, Android SDK | 핵심 앱 |
| UI | 차량용 디자인 가이드 준수, Compose 사용 가능성 확인 | 주행 중 UI 정책 및 Pleos 예제 우선 |
| 차량 데이터 | Pleos Vehicle SDK | 지원 메서드·권한 확인 필요 |
| 내비·위치 | NaviHelper / FusedLocation | 위치 필요 시 지원 및 동의 검증 |
| 음성 | TTS / STT | 운전 중 사용 사례가 있을 때만 |
| 로컬 보관 | JSON 로그 또는 Room | 먼저 장치 안에서 소규모 이벤트 기록 |
| 선택적 서버 | Java 17+ / Spring Boot | 다중 차량 집계 또는 원격 분석이 필요할 때 |
| 선택적 DB | PostgreSQL / 필요 시 PostGIS | 실제 데이터 모델 검증 후 |
| 버전 관리 | Git, GitHub | 설계 기록·구현·시연 산출물 |

공식 기본 조건: Android API 28 이상, Gradle 8.0 이상, Java 또는 Kotlin.
참고: https://appdevchallenge.auto-hmg.io/guide/development

## Windows에서 개발 환경을 준비하는 순서

1. Android Studio를 설치하고 Android SDK 및 에뮬레이터 실행 환경을 확인한다.
2. Pleos Playground 계정을 만들고 개발자용 자료 접근 여부를 확인한다.
3. 공식 Pleos Connect 개발 시작 가이드에서 **현재 지원되는** 시스템 이미지, SDK 버전, Gradle 설정을 확인한다.
4. 지원되는 Pleos Connect 에뮬레이터를 설치한다. 업로드한 Claude 조사에서는 'Emulator 2.1.0, API 34'를 언급하지만 다운로드 가능 버전은 다시 확인한다.
5. Virtual Console을 연결해 어떤 차량 신호를 넣고 바꿀 수 있는지 살펴본다.
6. 공식 예제 프로젝트를 실행하고 최소 한 종류의 상태 **읽기** API를 호출해 본다.
7. 앱 종료 및 재시작, 신호 미제공 시 처리, 에뮬레이터 네트워크 연결을 검증한다.
8. 설정과 결과는 `docs/development/data-capability-matrix.md`에 기록한다.

공식 개발 시작: https://developers.pleos.ai/playground/discover/developer-journey

**주의:** 설치를 실행한 기록이 없으므로 현재 상태는 '개발 환경 문서화'이고 '개발 환경 설치 완료'가 아니다.

## 아이디어와 상관없이 재사용할 수 있는 코드 구조(제안)

최종 앱을 정한 후에만 아래 예시에서 필요한 부분을 만든다.

```text
hyundai/
├── app/                               # 향후 Kotlin/Android Studio 프로젝트
│   └── src/main/java/.../
│       ├── ui/                        # 자동차용 화면
│       ├── domain/                    # 차량 데이터와 독립적인 판단 로직
│       ├── data/
│       │   ├── vehicle/               # Pleos SDK 어댑터
│       │   ├── external/              # 공공 API 클라이언트
│       │   └── local/                 # 로컬 기록
│       └── di/                        # 필요할 때만 의존성 주입
├── backend/                           # 다중 차량 수집이 필요할 때만 추가
├── apk/                               # release APK 제출 시
└── docs/                              # 이 저장소에서 먼저 준비하는 영역
```

**SDK와 비즈니스 코드를 분리**하는 것이 핵심이다. 차량 SDK 코드가 분석 알고리즘 안까지 직접 들어가면 SDK 없이 테스트하기가 어려워진다.

예컨대 `VehicleSignalSource` 같은 프로젝트 내부 인터페이스를 통해 상태 이벤트를 읽고, 실제 어댑터는 Pleos SDK를 사용하며, 테스트 어댑터는 기록한 샘플을 재생하도록 설계할 수 있다. 이 이름은 **우리 프로젝트에서 제안하는 인터페이스 이름**이지 Pleos 공식 SDK 메서드가 아니다.

## 제안 데이터 흐름

```text
Pleos 차량 상태(읽기/구독)
      |
      v
이벤트 표준화(유형, 값, 관측 시각, 출처, 권한 상태)
      |
      +--> 로컬 큐/기록 --> 사용자 동의 --> 선택적 서버 전송
      |
      +--> 외부 데이터 연결(기상, 장소, 경로 등)
      |
      v
아이디어별 분석 규칙 / 통계 / 예측오차 측정
      |
      v
차량 사용 맥락에 맞는 짧고 안전한 안내 UI
```

공통 이벤트의 **구조만 제안**한다. 실제 위치, 차량 식별, 좌석 데이터, 운전 습관 데이터는 필요하다고 증명되기 전까지 저장하지 않는다.

```text
eventId: UUID
observedAt: ISO-8601 timestamp
signalType: String
value: String 또는 명시적 타입
source: "pleos-sdk" | "virtual-console" | "sample"
quality: available | missing | simulated
```

이것은 DB 테이블을 지금 확정하자는 의미가 아니다. 아이디어를 결정한 뒤 최소한의 필드로 줄인다.

## 테스트 전략

- **SDK 확인:** 공식 예제가 동작하고 상태 변화를 읽는지.
- **결측 확인:** 지원하지 않는 차종·권한 미부여·오프라인일 때 앱이 종료되지 않는지.
- **재생 테스트:** 샘플 이벤트를 이용해 분석 결과가 재현되는지.
- **사용성:** 주행 중 긴 글과 조작을 요구하지 않는지, 안전한 시점에만 안내하는지.
- **가산점:** 1차 제출 전에 실제 동작하는 Android 앱이 있다면 화면 녹화, 소스, release APK로 증빙할 수 있는지.

### 지금 구현하지 않을 것

- 아직 확정되지 않은 아이디어의 API/DB 모델
- 사용자 개인정보나 실차 데이터를 업로드하는 서버
- 차량 제어, 자동 조향·제동·공조 같은 기능
- AI 에이전트, Kafka, K8s, 대규모 분산 인프라

## 외부 데이터 활용 시 주의

실시간 날씨를 사용한다면 기상청 API의 관측 주기·갱신 지연·대체값을 확인한다. 도로·충전소·지형 데이터도 사용 조건과 라이선스를 점검한다. 수집 시각과 실제 관측 시각이 다를 수 있으므로 두 시각을 구분해야 한다.

## 다음 환경 검증의 완료 기준

1. Android Studio에서 Pleos 에뮬레이터가 부팅된다.
2. 공식 SDK 예제로 한 가지 이상 차량 상태를 읽는다.
3. Virtual Console에서 그 값의 변화가 재현된다(가능한 경우).
4. 사용 권한과 미지원 시 동작을 기록한다.

위 항목은 **목표**이며 현재 완료된 사실이 아니다.
