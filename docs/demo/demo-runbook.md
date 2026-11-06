# 시연 런북 (장면별 재현 방법)

## 준비

1. 에뮬레이터 실행(권장 1920×1080 — 추천 2건이 한 화면에 보임). 해상도 변경: `adb shell wm size 1920x1080` (복구 `adb shell wm size reset`).
2. 앱 설치: `adb install -r apk\한영서_BabyRelay_release.apk` (디버그: `app\build\outputs\apk\debug\app-debug.apk`).
3. 초기화: `adb shell pm clear --user <am get-current-user 결과> com.babyrelay.app`.
4. 앱 실행. Pleos 서비스에 연결되지 않으면 약 6초 뒤 자동으로 **REPLAY 모드**로 전환되며 상단에 `REPLAY · 시뮬레이션 (실제 연동 아님)`이 표시된다. 연결되면 `PLEOS · 내비 연결됨`.
5. 개발·시연 패널: 상단 오른쪽 모드 태그를 누른다(출시 사용자용 아님).

화면 녹화: `adb shell screenrecord --time-limit 60 /sdcard/scene.mp4` 후 `adb pull`. (AAOS 다중 디스플레이에서는 `--display-id`가 필요할 수 있음)

## 장면별 절차 (REPLAY)

| 장면 | 절차 | 기대 화면 |
| --- | --- | --- |
| 2 출발 준비 | "여행 시작" → 목적지 칩 `대전역 · 내비`, `1시간`, `수유실`·`기저귀 교환대` 확인 | Plan 화면, 추천 계산됨 |
| 3 추천 | 추천 카드 2건의 이유 문장을 읽는다 | 직선 기준·60km/h 가정·확인일, 1건은 "1년 이상 지남" |
| 4 내비 연결 | "경유지로 등록" | "내비 확인 대기" → 약 0.4초 뒤 "내비에 등록됨" + 상단 `이동` 단계로 전환 |
| 4′ 미확인 재현 | 개발 패널 → `경유지 확인: 없음` → 등록 → 8초 대기 | "내비 확인 안 됨 · 직접 확인하세요" + 장소 이름·주소 |
| 5 경유지 도착 | 개발 패널 → `경유지 도착` | `휴식` 단계, 기록 버튼 활성 |
| 5′ 주행 제한 | 개발 패널 → `시뮬: 주행` | 빨간 배너, 기록 버튼 비활성. `시뮬: 정차`로 복구 |
| 5″ 실제 AAOS 값 | 패널 → `AAOS 플랫폼`, `adb shell cmd car_service inject-vhal-event 0x11400400 8` 등으로 주행 상태 주입(스크립트 `tools/demo_capture.py --platform-ux`) | 배너 출처 "플랫폼" |
| 6 돌봄 기록 | `수유 완료`, `기저귀 교환 완료`, `시설 이용 가능` | 버튼에 ✓와 기록 시각 |
| 7 목적지 도착 | `다시 출발` → 패널 → `목적지 도착` → 전달 물품 `기저귀`·`물티슈` | 인계 카드(분 전 표시, 물품, 시설 메모, REPLAY 태그) → `확인 완료 · 여정 종료` |
| 데이터 없음 | 새 여행 → 패널 → `데이터 없음` | "추천할 수 있는 시설이 없어요" + 안내, `출발 처리(수동)` 가능 |
| 중복 이벤트 | 경유지 도착 후 패널 → `중복 도착 이벤트` | 상태가 한 번만 바뀜(패널 최근 이벤트 로그) |
| 경로 취소 | 이동 중 패널 → `경로 취소` | "내비 경로가 취소되어 여정을 종료했어요" |

`REPLAY` 인계 카드의 "0분 전"은 이벤트를 빠르게 연속 재생했기 때문이다. 영상에서는 기록 사이에 대기하거나 자막으로 시간 경과를 표기한다.

## 자동 캡처

```powershell
python tools\demo_capture.py docs\design\screenshots\replay-aaos-1920x1080 --theme dark
python tools\demo_capture.py <출력폴더> --theme light --platform-ux   # AAOS 플랫폼 값 장면 포함
```

## 실제 Pleos 에뮬레이터 시연

`docs/development/sdk-integration.md`의 "실행 방법" 절을 따른다. 결과가 TESTED인 항목만 영상에서 `[Pleos 에뮬레이터]`로 표기한다.
