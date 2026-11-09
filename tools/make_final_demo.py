#!/usr/bin/env python3
"""심사용 시연 영상 조립 (1280x720, 30fps, 약 80초).

입력은 저장소에 이미 있는 증거 자료만 쓴다.
- docs/demo/video/scene-pleos-navi-real.mp4   실제 Pleos Connect Emulator 녹화
- docs/design/screenshots/replay-aaos-1920x720/  Replay 어댑터 화면 캡처

모든 장면에 출처(실제 Pleos / REPLAY 재현 / SAMPLE 데이터)를 화면 밖 여백에 표시한다.
자막은 앱 화면을 가리지 않는다.

사용법: python tools/make_final_demo.py [--font <한글 폰트 경로>] [--font-index N]
필요: ffmpeg, Pillow
"""
import argparse
import os
import subprocess
import tempfile

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SHOTS = os.path.join(ROOT, "docs/design/screenshots/replay-aaos-1920x720")
PLEOS_CLIP = os.path.join(ROOT, "docs/demo/video/scene-pleos-navi-real.mp4")
OUT = os.path.join(ROOT, "docs/demo/video/babyrelay-demo-final.mp4")

W, H, FPS = 1280, 720, 30
BG = (14, 15, 17)
INK = (240, 242, 245)
MUTED = (160, 166, 176)
BLUE = (92, 156, 255)
GREEN = (72, 199, 142)
AMBER = (240, 180, 70)
LINE = (44, 48, 56)


class Fonts:
    def __init__(self, path, bold_path, index):
        self.path, self.bold_path, self.index = path, bold_path, index
        self.cache = {}

    def get(self, size, bold=False):
        key = (size, bold)
        if key not in self.cache:
            p = self.bold_path if bold else self.path
            self.cache[key] = ImageFont.truetype(p, size, index=self.index)
        return self.cache[key]


def chip(d, x, y, text, color, f):
    """테두리 있는 출처 라벨. 오른쪽 끝 x를 돌려준다."""
    l, t, r, b = d.textbbox((0, 0), text, font=f)
    w, h = r - l, b - t
    d.rounded_rectangle([x, y, x + w + 24, y + h + 18], radius=8, outline=color, width=2)
    d.text((x + 12, y + 8 - t), text, font=f, fill=color)
    return x + w + 24


def center(d, y, text, f, fill=INK):
    l, t, r, b = d.textbbox((0, 0), text, font=f)
    d.text(((W - (r - l)) / 2, y), text, font=f, fill=fill)


def frame_base(fonts, step, title, chips, note):
    """위 여백: 단계와 제목. 아래 여백: 출처 라벨과 한 줄 설명."""
    im = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(im)
    d.text((48, 26), step, font=fonts.get(20, True), fill=BLUE)
    d.text((48, 54), title, font=fonts.get(32, True), fill=INK)
    x = 48
    for text, color in chips:
        x = chip(d, x, 640, text, color, fonts.get(18, True)) + 10
    if note:
        d.text((x + 8, 648), note, font=fonts.get(20), fill=MUTED)
    return im


def still_scene(fonts, shot, step, title, chips, note):
    im = frame_base(fonts, step, title, chips, note)
    s = Image.open(os.path.join(SHOTS, shot)).convert("RGB").resize((1184, 444), Image.LANCZOS)
    im.paste(s, (48, 148))
    ImageDraw.Draw(im).rectangle([47, 147, 48 + 1184, 148 + 444], outline=LINE, width=1)
    return im


def card(fonts, heading, lines, footer=None):
    im = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(im)
    total = 64 + len(lines) * 52
    y = (H - total) / 2 - 10
    center(d, y, heading, fonts.get(44, True))
    y += 88
    for text, color in lines:
        center(d, y, text, fonts.get(27), color)
        y += 52
    if footer:
        center(d, H - 70, footer, fonts.get(19), MUTED)
    return im


def table_card(fonts, heading, rows):
    im = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(im)
    center(d, 70, heading, fonts.get(38, True))
    y = 170
    for label, color, text in rows:
        chip(d, 150, y, label, color, fonts.get(20, True))
        d.text((330, y + 4), text, font=fonts.get(24), fill=INK)
        y += 74
    return im


def run(cmd):
    subprocess.run(cmd, check=True)


def png_to_clip(png, seconds, out):
    fade_out = max(seconds - 0.35, 0)
    run(["ffmpeg", "-v", "error", "-y", "-loop", "1", "-t", str(seconds), "-i", png,
         "-vf", f"fps={FPS},fade=t=in:d=0.35,fade=t=out:st={fade_out}:d=0.35,format=yuv420p",
         "-c:v", "libx264", "-preset", "veryfast", "-crf", "20", "-an", out])


def pleos_clip(fonts, work, out):
    """실제 Pleos 녹화본. 원본 하단의 초안 자막 띠를 잘라내고 2배속으로 넣는다."""
    src_start, src_len, speed = 3.0, 48.0, 2.0
    out_len = src_len / speed
    chips = [("실제 Pleos Connect Emulator v2.1.0", GREEN), ("NaviHelper SDK 3.0.5", GREEN), ("2배속", MUTED)]
    captions = [
        (0.0, 5.5, "후보를 누르면 차량 내비에 경유지 등록을 요청합니다"),
        (5.5, 17.5, "Pleos 내비 경로에 경유지가 들어갑니다 (경유지 1개 포함)"),
        (17.5, out_len, "내비의 경로 시작 이벤트를 받아 앱이 '이동 중'으로 바뀝니다"),
    ]
    inputs, filters, last = [], [], "base"
    filters.append(
        f"[0:v]trim=start={src_start}:duration={src_len},setpts=(PTS-STARTPTS)/{speed},"
        f"fps={FPS},crop=1280:644:0:0,scale=1034:520[v]")
    for i, (t0, t1, text) in enumerate(captions):
        bg = frame_base(fonts, "2단계  경유지 연결", text, chips, "")
        p = os.path.join(work, f"pleos_bg{i}.png")
        bg.save(p)
        inputs += ["-loop", "1", "-t", str(out_len), "-i", p]
    # 배경 3장을 시간대별로 바꿔 깔고 그 위에 녹화본을 얹는다
    filters.append(f"[1:v]fps={FPS}[base]")
    for i in range(1, len(captions)):
        t0, t1, _ = captions[i]
        filters.append(f"[{last}][{i + 1}:v]overlay=enable='between(t,{t0},{t1})'[b{i}]")
        last = f"b{i}"
    filters.append(f"[{last}][v]overlay=123:110:shortest=1,"
                   f"fade=t=in:d=0.35,fade=t=out:st={out_len - 0.35}:d=0.35,format=yuv420p[o]")
    run(["ffmpeg", "-v", "error", "-y", "-i", PLEOS_CLIP, *inputs,
         "-filter_complex", ";".join(filters), "-map", "[o]", "-t", str(out_len),
         "-c:v", "libx264", "-preset", "veryfast", "-crf", "21", "-an", out])


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--font", default="/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc")
    ap.add_argument("--font-bold", default="/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc")
    ap.add_argument("--font-index", type=int, default=1, help="ttc 안의 한국어 글꼴 번호 (ttf면 0)")
    a = ap.parse_args()
    fonts = Fonts(a.font, a.font_bold, a.font_index)
    replay = [("REPLAY 재현 (실제 연동 아님)", AMBER), ("SAMPLE 데이터", AMBER)]

    scenes = []  # (종류, 내용, 초)
    scenes.append(("png", card(fonts, "BabyRelay", [
        ("쉴 곳을 출발 전에 정하면 차량 내비가 경유지로 데려가고", INK),
        ("도착하면 다음 돌봄 화면이 저절로 열립니다", INK),
    ], "2026 현대자동차그룹 차량용 앱 개발 공모전   Pleos Connect 차량용 앱 프로토타입"), 4.5))
    scenes.append(("png", card(fonts, "운전석의 보호자는 혼자입니다", [
        ("아기가 보채도 운전 중에는 수유실을 찾을 수 없습니다", MUTED),
        ("찾은 장소는 차량 내비에 다시 입력해야 합니다", MUTED),
        ("도착하면 마지막 수유 시각과 챙긴 물품을 기억에 의존합니다", MUTED),
    ]), 6.5))
    scenes.append(("png", still_scene(fonts, "02-plan-dark.png", "1단계  출발 전 계획",
                   "휴식 시각과 필요한 시설을 고르면 후보와 이유를 보여줍니다", replay,
                   "직선거리 기준임을 화면에 표시"), 7.5))
    scenes.append(("pleos", None, 24.0))
    scenes.append(("png", still_scene(fonts, "07b-platform-driving-dark.png", "3단계  주행 중",
                   "차량의 주행 제한 신호가 켜지면 모든 입력이 잠깁니다",
                   [("AAOS 에뮬레이터 플랫폼 값", GREEN), ("SAMPLE 데이터", AMBER)],
                   "CarUxRestrictions"), 6.0))
    scenes.append(("png", still_scene(fonts, "06-records-dark.png", "4단계  경유지 도착",
                   "도착 이벤트가 돌봄 기록 화면을 엽니다. 정차 후 버튼만 누릅니다", replay, ""), 7.0))
    scenes.append(("png", still_scene(fonts, "08-arrival-dark.png", "5단계  목적지 도착",
                   "이동 중 기록이 인계 카드로 정리됩니다", replay,
                   "아이 이름과 위치 이력은 저장하지 않음"), 7.0))
    scenes.append(("png", table_card(fonts, "검증 범위", [
        ("실제 확인", GREEN, "Pleos 에뮬레이터에서 경유지 포함 경로 요청과 내비 반영, 경로 취소"),
        ("실제 확인", GREEN, "서명된 release APK 설치와 실행, 주행 제한 값 읽기"),
        ("재현만", AMBER, "경유지와 목적지 도착 콜백 (Replay 어댑터와 단위 테스트)"),
        ("샘플", AMBER, "시설 데이터 14건은 합성 데이터"),
        ("2차 전 개발", BLUE, "도착 콜백 실검증, 공공 데이터 교체, 음성 급정차"),
    ]), 9.0))
    scenes.append(("png", card(fonts, "BabyRelay", [
        ("계획은 보호자가, 실행은 차량이", INK),
        ("서버 없음   기기 안에만 저장   차량 제어 권한 없음", MUTED),
    ], "소스와 APK: GitHub 저장소 kangwoul2/hyundai"), 5.0))

    with tempfile.TemporaryDirectory() as work:
        parts = []
        for i, (kind, content, sec) in enumerate(scenes):
            out = os.path.join(work, f"p{i:02d}.mp4")
            if kind == "png":
                png = os.path.join(work, f"p{i:02d}.png")
                content.save(png)
                png_to_clip(png, sec, out)
            else:
                pleos_clip(fonts, work, out)
            parts.append(out)
        lst = os.path.join(work, "list.txt")
        with open(lst, "w") as f:
            f.writelines(f"file '{p}'\n" for p in parts)
        run(["ffmpeg", "-v", "error", "-y", "-f", "concat", "-safe", "0", "-i", lst,
             "-c:v", "libx264", "-preset", "veryfast", "-crf", "22", "-pix_fmt", "yuv420p",
             "-movflags", "+faststart", "-an", OUT])
    print("wrote", OUT)


if __name__ == "__main__":
    main()
