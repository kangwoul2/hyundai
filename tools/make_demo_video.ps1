# 영상 A 초안 조립: 제목 카드 + [Pleos 에뮬레이터 실녹화] + [REPLAY 녹화(2.5배속)] + 기술 설명 카드
# 전제: ffmpeg 설치, 입력은 adb screenrecord 원본 2개. 모든 장면에 출처 자막을 넣는다.
param(
  [string]$Ffmpeg = 'C:\SSAFY\tools\ffmpeg\ffmpeg-9.0.2-essentials_build\bin\ffmpeg.exe',
  [string]$PleosRaw = 'pleos_scene.mp4',
  [string]$ReplayRaw = 'replay_scene.mp4',
  [string]$Out = 'docs\demo\video\babyrelay-demo-A-draft.mp4',
  [string]$Work = "$env:TEMP\babyrelay_video"
)
New-Item -ItemType Directory -Force $Work | Out-Null
$font = 'C\:/Windows/Fonts/malgun.ttf'
$utf8 = New-Object System.Text.UTF8Encoding($false)

function Card($name, $text, $sec) {
  $txt = Join-Path $Work "$name.txt"
  [IO.File]::WriteAllText($txt, $text, $utf8)
  $t = ($txt -replace '\\', '/') -replace ':', '\:'
  & $Ffmpeg -v error -y -f lavfi -i "color=c=0x0E0F11:s=1280x720:d=${sec}:r=30" `
    -vf "drawtext=fontfile='$font':textfile='$t':fontcolor=white:fontsize=40:line_spacing=18:x=(w-text_w)/2:y=(h-text_h)/2" `
    -c:v libx264 -pix_fmt yuv420p -an (Join-Path $Work "$name.mp4")
}

function Clip($name, $src, $capText, $ss, $dur, $speed) {
  $txt = Join-Path $Work "$name.txt"
  [IO.File]::WriteAllText($txt, $capText, $utf8)
  $t = ($txt -replace '\\', '/') -replace ':', '\:'
  & $Ffmpeg -v error -y -ss $ss -t $dur -i $src `
    -vf "setpts=PTS/$speed,fps=30,drawtext=fontfile='$font':textfile='$t':fontcolor=white:fontsize=26:box=1:boxcolor=black@0.65:boxborderw=10:x=(w-text_w)/2:y=h-th-24" `
    -c:v libx264 -preset veryfast -crf 26 -pix_fmt yuv420p -an (Join-Path $Work "$name.mp4")
}

Card 'c1' "아기와 장거리 이동`n쉴 곳 찾고, 내비에 다시 넣고, 기록은 흩어진다`n`n[프로토타입 자막]" 4
Clip 'v1' $PleosRaw "[Pleos 에뮬레이터 · 실제 NaviHelper] 경유지 포함 경로 요청 → 내비 경로 → 앱 '이동 중'" 3 38 1
Clip 'v2' $ReplayRaw "[REPLAY 시뮬레이션 · SAMPLE 데이터 · 2.5배속] 추천 → 휴식 기록 → 주행 제한 잠금 → 인계 카드" 0 124 2.5
Card 'c2' "내비 이벤트 → 상태 머신 / 주행 제한(CarUxRestrictions)은 별도`n서버 없음 · 기기 내 저장 · PLEOS / REPLAY 항상 표시`n`n경유지·목적지 도착 콜백의 실제 Pleos 검증은 미완료(REPLAY 검증)" 6

$list = Join-Path $Work 'list.txt'
"file 'c1.mp4'`nfile 'v1.mp4'`nfile 'v2.mp4'`nfile 'c2.mp4'" | Set-Content $list -Encoding ascii
& $Ffmpeg -v error -y -f concat -safe 0 -i $list -c copy $Out
Get-Item $Out | Select-Object Name, Length
