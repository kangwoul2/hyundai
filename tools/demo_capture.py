#!/usr/bin/env python3
"""BabyRelay REPLAY 시연 장면을 에뮬레이터에서 자동 구동하며 스크린샷을 남긴다.

전제: 에뮬레이터에 앱이 설치되어 있고 REPLAY 모드로 동작한다(Pleos 연결 실패 후 자동 전환 또는 개발 패널에서 전환).
출력은 REPLAY 시뮬레이션 화면이며 실제 Pleos SDK/차량 검증 증거가 아니다.

사용: python tools/demo_capture.py docs/design/screenshots/replay [--theme dark|light]
"""
import os
import subprocess
import sys
import time

sys.path.insert(0, os.path.dirname(__file__))
import emu  # noqa: E402


def wait(sec=1.2):
    time.sleep(sec)


def dev_panel(*taps):
    """상단 모드 배지를 눌러 개발 패널을 열고 항목을 순서대로 누른 뒤 닫는다."""
    emu.tap_text("REPLAY ·")
    wait(1.0)
    for t in taps:
        emu.tap_text(t)
        wait(0.8)
    emu.tap_text("닫기", exact=True)
    wait(1.0)


def vhal(gear, speed, brake):
    """에뮬레이터 VHAL 속성 주입: 기어/속도/주차 브레이크 (Google AAOS 에뮬레이터 기준)."""
    emu.adb("shell", "cmd", "car_service", "inject-vhal-event", "0x11200402", str(brake))
    emu.adb("shell", "cmd", "car_service", "inject-vhal-event", "0x11400400", str(gear))
    emu.adb("shell", "cmd", "car_service", "inject-vhal-event", "0x11600207", str(speed))


def main():
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    theme = "dark" if "--theme" in sys.argv and sys.argv[sys.argv.index("--theme") + 1] == "dark" else "light"
    emu.adb("shell", "cmd", "uimode", "night", "yes" if theme == "dark" else "no")
    # AAOS는 다중 사용자(Driver=현재 사용자)이므로 현재 사용자의 앱 데이터를 지운다.
    user = emu.adb("shell", "am", "get-current-user").stdout.strip() or "0"
    emu.adb("shell", "pm", "clear", "--user", user, "com.babyrelay.app")
    emu.adb("shell", "am", "start", "-n", "com.babyrelay.app/.MainActivity")
    wait(9)  # Pleos 연결 시도(최대 6초) 후 REPLAY로 전환

    def shot(name):
        emu.shot(os.path.join(out, f"{name}-{theme}.png"))
        print("shot", name)

    shot("01-idle")
    emu.tap_text("여행 시작"); wait(2)
    shot("02-plan")
    emu.tap_text("경유지로 등록"); wait(0.5)
    shot("03-requested")
    wait(2.0)
    shot("04-driving")
    dev_panel("경유지 도착"); wait(1)
    shot("05-stop")
    emu.tap_text("수유 완료"); wait(0.5)
    emu.tap_text("기저귀 교환 완료"); wait(0.5)
    emu.tap_text("시설 이용 가능"); wait(1)
    shot("06-records")
    dev_panel("시뮬: 주행")
    shot("07-restricted")
    dev_panel("시뮬: 정차")
    if "--platform-ux" in sys.argv:
        # 실제 AAOS CarUxRestrictions 값으로 화면 제한이 바뀌는지 확인한다(에뮬레이터 VHAL 주입).
        dev_panel("AAOS 플랫폼")
        vhal(gear=8, speed=15.0, brake=0)  # D 기어, 15m/s, 주차 브레이크 해제
        wait(2.5)
        shot("07b-platform-driving")
        vhal(gear=4, speed=0.0, brake=1)  # P 기어, 정지, 주차 브레이크
        wait(2.5)
        shot("07c-platform-parked")
        dev_panel("시뮬: 정차")
    emu.tap_text("다시 출발"); wait(1)
    dev_panel("목적지 도착"); wait(1)
    emu.tap_text("기저귀", exact=True); wait(0.4)
    emu.tap_text("물티슈", exact=True); wait(1)
    shot("08-arrival")
    emu.tap_text("확인 완료"); wait(1)
    shot("09-closed")


if __name__ == "__main__":
    main()
