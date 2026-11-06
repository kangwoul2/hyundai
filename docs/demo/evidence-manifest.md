# 증거 목록 (Evidence Manifest)

각 증거의 **출처 라벨**: `[Pleos 에뮬레이터]` 실제 Pleos SDK+에뮬레이터 · `[AAOS 에뮬레이터]` Google AAOS 이미지(Pleos 아님) · `[REPLAY]` Replay Adapter · `[SAMPLE]` 합성 시설 · `[프로토타입]` 자막/도식.
모든 시설 데이터는 `[SAMPLE]`이다.

## 빌드·테스트

| 증거 | 위치 | 라벨 |
| --- | --- | --- |
| JVM 단위 테스트 72건 통과 | [test-results.md](../testing/test-results.md), [logs/unit-test-run-1.txt](../testing/logs/unit-test-run-1.txt)(70건 시점) | JVM |
| 계측 테스트 4건 통과 | test-results.md 2절 | [AAOS 에뮬레이터] |
| CarUxRestrictions 주행(true)/주차(false) 값 | test-results.md, `replay-aaos-1920x720/07b…`, `07c…` | [AAOS 에뮬레이터] VHAL 주입 |
| Release APK 클린 설치 | `apk/한영서_BabyRelay_release.apk`, `pleos-emulator-2560x1440/00-…` | [Pleos 에뮬레이터], [AAOS 에뮬레이터] |

### APK

| 파일 | SHA-256 | 크기 |
| --- | --- | --- |
| `apk/한영서_BabyRelay_release.apk` (v0.1.0-mvp, v2 서명, DN `CN=BabyRelay, OU=Contest, O=BabyRelay, C=KR`) | `cd2c5e408be79987cb590c3f6fd3c7f6d570507313bfa34fd92c0e0f315d8f26` | 19,668,315 B |

서명 키(`C:\SSAFY\keys\babyrelay-release.jks`)와 비밀번호(`keystore.properties`)는 저장소 밖/제외이며 **사용자가 별도 백업해야 한다**(분실 시 같은 서명으로 업데이트 불가).

## 실제 Pleos 에뮬레이터 (G1)

| 증거 | 위치 |
| --- | --- |
| 연결·경로·콜백 로그 | [g1-pleos-emulator-log.txt](../development/g1-pleos-emulator-log.txt), [g1-pleos-emulator-cancel-log.txt](../development/g1-pleos-emulator-cancel-log.txt) |
| 화면 캡처 | `docs/design/screenshots/pleos-emulator-2560x1440/` (연결, 추천, 내비 경로(경유지 포함), 경로 취소 처리) |
| 화면 녹화 | `docs/demo/video/scene-pleos-navi-real.mp4` |
| 결과 해석·미검증 항목 | [sdk-integration.md](../development/sdk-integration.md) |

## REPLAY / UI

| 증거 | 위치 | 라벨 |
| --- | --- | --- |
| 전 과정 스크린샷 | `docs/design/screenshots/replay-aaos-1920x720/`(다크·라이트), `replay-aaos-1920x1080/`, `replay-pleos-2560x1440/` | [REPLAY] [SAMPLE] |
| 전 과정 녹화 | `docs/demo/video/scene-replay-flow.mp4` | [REPLAY] [SAMPLE] |
| 영상 A 초안(약 98초) | `docs/demo/video/babyrelay-demo-A-draft.mp4` | 장면별 자막 라벨 |
| 재현 스크립트 | `tools/demo_capture.py`, `tools/emu.py`, `tools/make_demo_video.ps1` | |

## 장면별 출처 (shot-list와 일치)

| 장면 | 출처 |
| --- | --- |
| 출발 준비·추천 | [REPLAY]/[Pleos 에뮬레이터](Pleos에서도 동일 UI, 위치는 내비 값) + [SAMPLE] |
| 내비 연결(경유지 등록) | **[Pleos 에뮬레이터] 실제** |
| 경유지 도착·돌봄 기록·인계 카드 | [REPLAY] (실제 도착 콜백 미검증) |
| 주행 제한 잠금 | [REPLAY] 시뮬레이션 값 / [AAOS 에뮬레이터] 실제 플랫폼 값 |

## 없는 것 (숨기지 않음)

- 실제 공공데이터 기반 시설 데이터
- 실제 Pleos 도착 콜백 로그/영상
- Pleos 이미지에서 주행 중 `CarUxRestrictions=true` 전환 증거
- 영상 B(9:16 숏폼) — 제작하지 않음. 영상 A 장면에서 재편집 필요
- 실차, 사용성 시험
