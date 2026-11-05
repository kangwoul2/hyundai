#!/usr/bin/env python3
"""에뮬레이터 조작/캡처 도우미 (검증·시연 녹화용). adb 경로는 ANDROID_HOME 또는 --adb 로 지정한다.

예) python tools/emu.py shot out.png            # 스크린샷
    python tools/emu.py tap "여행 시작"          # 화면의 텍스트를 찾아 탭
    python tools/emu.py texts                   # 현재 화면의 텍스트 목록
"""
import os
import re
import subprocess
import sys
import tempfile
import time
import xml.etree.ElementTree as ET

ADB = os.path.join(os.environ.get("ANDROID_HOME", r"C:\SSAFY\tools\android-sdk"), "platform-tools", "adb.exe")


def adb(*args, check=True, text=True):
    return subprocess.run([ADB, *args], capture_output=True, text=text, check=check)


def display_id():
    out = adb("shell", "dumpsys", "SurfaceFlinger", "--display-id").stdout
    m = re.search(r"Display (\d+) \(HWC display 0\)", out)
    return m.group(1) if m else None


def shot(path):
    d = display_id()
    cmd = ["shell", "screencap"] + (["-d", d] if d else []) + ["-p", "/sdcard/_s.png"]
    adb(*cmd)
    adb("pull", "/sdcard/_s.png", path)
    return path


def nodes():
    adb("shell", "uiautomator", "dump", "/sdcard/_ui.xml")
    with tempfile.TemporaryDirectory() as d:
        p = os.path.join(d, "ui.xml")
        adb("pull", "/sdcard/_ui.xml", p)
        root = ET.parse(p).getroot()
    out = []
    for n in root.iter("node"):
        text = n.get("text") or n.get("content-desc") or ""
        b = re.findall(r"\d+", n.get("bounds") or "")
        if text and len(b) == 4:
            x1, y1, x2, y2 = map(int, b)
            out.append((text, (x1 + x2) // 2, (y1 + y2) // 2, (x1, y1, x2, y2), n.get("enabled") == "true"))
    return out


def find(substr, exact=False):
    for text, cx, cy, box, en in nodes():
        if (text == substr) if exact else (substr in text):
            return text, cx, cy, box, en
    return None


def tap_text(substr, exact=False, retries=3):
    for _ in range(retries):
        hit = find(substr, exact)
        if hit:
            adb("shell", "input", "tap", str(hit[1]), str(hit[2]))
            return hit[0]
        time.sleep(0.8)
    raise SystemExit(f"텍스트를 찾을 수 없음: {substr}")


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    cmd = sys.argv[1]
    if cmd == "shot":
        print(shot(sys.argv[2]))
    elif cmd == "tap":
        print("tapped:", tap_text(sys.argv[2], exact="--exact" in sys.argv))
    elif cmd == "texts":
        for t, cx, cy, box, en in nodes():
            print(f"{'*' if en else ' '} {t!r} @ {box}")
    else:
        sys.exit("unknown command")
