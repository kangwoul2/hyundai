# Windows 개발 환경 설정 (실제 사용한 구성)

검증 환경: Windows 11 Enterprise 10.0.26200, Intel i7-11700, RAM 32GB, WHPX 사용 가능. 관리자 권한 없이 사용자 폴더(`C:\SSAFY\tools`)에 설치했다.

## 도구

| 도구 | 버전 | 비고 |
| --- | --- | --- |
| JDK | Temurin 17.0.21 | 시스템 기본은 Java 8이므로 `JAVA_HOME`을 명시해야 한다 (AGP 8.5는 JDK 17 필요) |
| Android SDK | cmdline-tools 11076708, platform 34, build-tools 34.0.0, emulator, platform-tools | |
| Gradle | 래퍼 8.9 (`gradlew.bat`) | 공식 조건: Gradle 8.0 이상 |
| 에뮬레이터 이미지 | `system-images;android-34-ext9;android-automotive;x86_64` (Google AAOS) | 일반 AAOS 검증용 |
| Pleos 이미지 | `crp_car_emulator.zip` (Pleos Connect v2.1.0, API 34) | 라이선스 동의 후 설치. `docs/development/sdk-integration.md` |

## 빌드·테스트 명령 (PowerShell)

```powershell
$env:JAVA_HOME='C:\SSAFY\tools\jdk\jdk-17.0.21+9'
$env:ANDROID_HOME='C:\SSAFY\tools\android-sdk'
$env:Path="$env:JAVA_HOME\bin;$env:Path"

./gradlew.bat :app:testDebugUnitTest          # JVM 단위 테스트
./gradlew.bat :app:assembleDebug              # 디버그 APK
./gradlew.bat :app:connectedDebugAndroidTest  # 계측 테스트(에뮬레이터 실행 중)
./gradlew.bat :app:assembleRelease            # 릴리스 APK (keystore.properties 필요)
```

`local.properties`(gitignore 대상)에는 `sdk.dir=C\:\\SSAFY\\tools\\android-sdk`를 둔다. **Git Bash의 `/c/...` 경로를 `ANDROID_HOME`에 넣으면 Gradle이 실패한다**(Windows 경로 사용).

## 에뮬레이터

```powershell
$env:ANDROID_AVD_HOME='C:\SSAFY\tools\avd'
# AVD 설정 예: hw.lcd.width=1920, hw.lcd.height=720(또는 1080), hw.lcd.density=240, hw.ramSize=4096M
emulator -avd aaos34 -no-snapshot -no-audio -gpu swiftshader_indirect
```

AAOS는 다중 사용자(현재 사용자 "Driver")이므로 앱 데이터 초기화는 `pm clear --user <am get-current-user> com.babyrelay.app`을 쓴다. 스크린샷은 디스플레이가 여러 개라 `screencap -d <id>`가 필요하다(`tools/emu.py`가 처리).

## 서명 키 (저장소에 올리지 않음)

릴리스 서명은 루트의 `keystore.properties`(gitignore 대상)와 키스토어 파일(`*.jks`, gitignore 대상)로만 주입한다.

```properties
storeFile=../keys/babyrelay-release.jks
storePassword=...
keyAlias=babyrelay
keyPassword=...
```

## 알려진 함정

- Bash 도구에서 `&`로 이어 백그라운드 실행하면 `cd`가 서브셸에만 적용된다.
- Windows 기본 콘솔 인코딩(cp949) 때문에 파이썬 스크립트 출력은 `sys.stdout.reconfigure(encoding="utf-8")`가 필요하다(도구에 반영됨).
- 테스트에서 `advanceUntilIdle()`은 `backgroundScope` 작업을 실행하지 않는다 → `runCurrent()`를 사용한다.
