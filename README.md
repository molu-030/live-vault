# Live Vault 3.9.0 — Android native preview

**3.9.0-preview / versionCode 27**은 Galaxy Tab S9 Ultra 가로 화면을 지원하는 개인용 체크포인트입니다. 큰 LCD·입출력과 반주·효과를 좌우에 배치하고, 작곡기는 코드와 트랙을 각각 스크롤합니다. 상단 바로가기와 하단 고정 녹음/재생 조작을 제공합니다. [태블릿 배치·화면·검증 범위](docs/LIVE_VAULT_V3_9_0_TABLET.md). **대표 24 + 드럼**, 키트 즐겨찾기, 파트 화살표와 프레이즈 보존을 고친 [3.8.8](docs/LIVE_VAULT_V3_8_8_COMPOSER_AUDIT.md), [종료·유휴 전력 개선](docs/LIVE_VAULT_V3_8_7_EXIT_AND_POWER.md), [영상 레이아웃 수정](docs/LIVE_VAULT_V3_8_6_VIDEO_LAYOUT.md)을 포함합니다. 이번 산출물은 **personal 전용**이며 공개 standard APK는 기존 **3.6.0-preview / versionCode 16** 파일을 유지합니다.

라이브의 **반주 선택 ▾** 또는 **☰ → 반주**에서 **내 파일 · 작곡기 · 온라인**을 엽니다. 반주가 없는 상태에서 재생을 눌러도 선택 화면이 열립니다. [반주 진입 복구](docs/LIVE_VAULT_V3_8_3_BACKING_RESTORE.md)와 [하울링 보호 검수](docs/LIVE_VAULT_V3_8_2_FEEDBACK_REVIEW.md)는 이력으로 유지합니다.

LIVE SESSION LCD에 실제 IN/OUT dB·음 이름·Hz·cents·녹음 시간과 상태를 표시합니다. 메인은 마이크·모니터링·입출력 장치 선택, 마이크/반주/리버브/에코 조절기, 반주 재생과 녹음/영상 하단 조작으로 구성합니다. 상세 효과는 **상세 ▾**, 장치는 **입력/출력 ▾**, 반주는 **반주 선택 ▾**에서 엽니다. 정적 LCD 격자와 고리는 장식이며 가짜 파형은 사용하지 않습니다.

반주 → 온라인에서 YouTube 주소/동영상 ID를 선택하고 공식 플레이어에서 직접 재생합니다. 같은 반주 음량 조절기로 플레이어 음량을 바꾸며 시스템 전체 음량과 마이크 게인은 유지합니다. YouTube 선택 시에는 마이크만 녹음하고, 화면을 벗어나면 동영상을 일시정지합니다. 플레이어 화면은 OFFLINE fixture로 구분하며 실제 기기의 YouTube·오디오·장치·터치 접근성·지연 검증과 배포 전 정책 검토는 남아 있습니다. 개인용 오버레이/APK와 비공개 전달 URL은 공개 저장소에 포함하지 않습니다. 이전 [3.7 검증](docs/LIVE_VAULT_V3_7_VALIDATION.md)과 [렌더 72장](docs/screenshots/v3.7/)은 이력으로 유지합니다.

## 화면과 기능

실시간 마이크 모니터링, 보컬 효과, 단선율 MIDI 채보, 반주, 카메라 녹화를 위한 Android 프로젝트입니다.

> **현재 상태: 개발 중인 체크포인트.** Galaxy S26 Ultra용 기존 배치와 S9 Ultra 가로 태블릿 배치를 지원합니다. 실제 창 크기와 시스템 영역으로 배치하고 녹음·영상·연습의 주 조작 버튼을 하단에 고정합니다. 색상과 로고는 유지합니다. [휴대폰 배치](docs/GALAXY_S26_ULTRA_LAYOUT.md) · [태블릿 배치와 검증 범위](docs/LIVE_VAULT_V3_9_0_TABLET.md).

## 미리보기 APK

기존 standard APK의 버전·실제 해시는 [산출물 안내](artifacts/README.md), 이번 개인용 체크포인트의 범위는 [3.9.0 태블릿 지원](docs/LIVE_VAULT_V3_9_0_TABLET.md)을 확인하세요. 이전 버전의 명세와 검증 기록은 당시 이력으로 유지합니다.

## AI로 반주 수정하기

1. 작곡기에서 **AI 내보내기**를 열고 선택적으로 원하는 수정 내용을 적은 뒤 ZIP을 저장합니다.
2. ZIP을 ChatGPT 등 AI에 첨부하고 완전한 프로젝트 JSON을 요청합니다. ZIP에는 오디오가 들어 있지 않습니다.
3. AI의 응답을 UTF-8 `.json` 파일로 저장하고 작곡기의 **JSON 불러오기** 또는 프로젝트 메뉴의 **AI 수정본 불러오기**를 선택합니다. 미리보기를 확인한 뒤 새 복사본으로 엽니다. 기존 프로젝트는 보관함에 저장됩니다.

가져오기는 최대 8 MiB의 일반 JSON을 받습니다. 파일을 읽는 중 화면이 재생성되면 다시 선택해 주세요.

## 백그라운드 예약 녹음

즉시·3초·5초·10초 시작, 앱 전환 후 녹음, 알림에서 취소/저장을 지원합니다. **standard는 마이크 전용 배포용**, **personal은 Android 내부 오디오 캡처 옵션이 있는 개인용**입니다. 개인용 APK와 캡처 구현은 공개 저장소에 올리지 않습니다. 개인용 flavor는 별도 비공개 소스 오버레이가 있을 때만 활성화합니다. [사용 방법과 빌드 차이](docs/BACKGROUND_RECORDING.md)를 확인하세요.

## 스피커 모드

이어폰 / 휴대폰 스피커 / 연결된 블루투스 스피커로 내 목소리를 보낼 수 있습니다. 스피커 모드는 휴대폰 내장 마이크, 낮은 시작 출력, 지원 기기의 에코 제거, 피드백 필터/자동 음소거를 사용합니다. 블루투스 전송 지연과 실제 음향 피드백은 기기별 확인이 필요합니다. [사용 방법과 보호 범위](docs/SPEAKER_MODE.md).

## 주요 기능

- 라이브·반주·보관함 3탭, 오디오/녹음 설정·보컬 연습·도움말 전체 메뉴
- 승인한 이중 원호 마이크 SVG와 Android 벡터, 블랙·차콜·네온 옐로 테마
- 출력 버퍼 1~8단계, 녹음 전 ±500ms 보컬·반주 싱크와 5초 테스트
- 두 방식의 스케일 연습, 목표 피치 그래프, 선택적 WAV/MIDI/결과 저장

[확정 명세](docs/LIVE_VAULT_V2_SPEC.md) · [벡터 원본](design/brand/live-vault-mark.svg)

## 이름·로고 및 UI 기준

사용자가 지정한 화면 코드 `MainActivity.java`와 공통 코드 `StudioTheme.java`를 기준으로 유지합니다. 추가된 스피커 설정은 팝업으로 옮기고, 녹음 예약 설정은 **전체 메뉴 → 녹음 설정**에서 바꿉니다. 라이브 메인에는 **⋯** 버튼을 두지 않습니다. 녹음 중 다른 탭에 나타나는 녹음 바에도 **⋯**를 둡니다. 명세의 Noto Sans KR·Roboto Mono를 앱에 포함했습니다. [브랜드 적용과 확인 범위](docs/BRANDING.md).

## 디자인 v2 통합

기존 `562c11f`의 네이티브 디자인 v2, 최근 반주 파일, 카메라 전환·일시정지 및 WAV/MIDI 동기화 기능을 유지했습니다. [디자인 결과](docs/DESIGN_RESULT.md). 신규 예약 녹음·스피커 기능은 동일한 화면 구성에 추가했습니다.

## Figma 작업 시작

- [화면·상태·컴포넌트 명세](docs/FIGMA_HANDOFF.md)
- [Figma에 붙여넣을 디자인 브리프](docs/FIGMA_PROMPT.md)
- [설계 토큰 JSON](docs/design-tokens.json)
- [제품 범위와 기술 제약](docs/PRODUCT.md)

Figma에서는 화면을 자유롭게 재설계해도 됩니다. 핵심 오디오 엔진과 UI를 분리했으므로 화면 구성을 변경할 수 있습니다. 현재 UI 진입점은 `app/src/main/java/pro/livemic/studio/MainActivity.java`입니다.

## 기술 구성

- Java 17 / Android SDK 35, Android 9(API 28) 이상
- C++17 / NDK 27.2 / AAudio full-duplex 저지연 스트림
- 네이티브 3밴드 EQ, 컴프레서, 알고리즘 리버브, 피드백 딜레이
- YIN 기반 보컬 음정 분석 / Standard MIDI File Format 0, 480 PPQ
- WAV PCM16 스테레오 녹음 / Camera2 + MediaRecorder(H.264) / AAC 변환 + MP4 결합
- 앱 전용 보관함 / Android 문서 선택기를 통한 파일 저장 / 콘텐츠 URI 공유
- 프런트엔드 라이브러리, 서버, 로그인, 클라우드 업로드 없음

## 빌드

Android Studio에서 폴더를 열고 SDK/NDK 설치 안내를 따르세요. 명령행 빌드는 JDK 17 또는 21, Android SDK가 필요합니다.

```sh
# local.properties는 각 기기에서 생성하며 Git에 포함하지 않습니다.
printf 'sdk.dir=/absolute/path/to/Android/Sdk\n' > local.properties
./gradlew :app:assembleStandardDebug
```

필요 패키지: `platforms;android-35`, `build-tools;35.0.0`, `ndk;27.2.12479018`, `cmake;3.22.1`.

APK 출력: `app/build/outputs/apk/standard/debug/app-standard-debug.apk` (개인용은 비공개 오버레이가 있을 때 별도 빌드).
ABI: `arm64-v8a`, `armeabi-v7a`, `x86_64`.

현재 release 설정도 개발용 debug 키를 사용합니다. **스토어 배포용 서명키는 포함하지 않습니다.** 실제 배포 전 소유자의 서명키로 release 설정을 변경하세요.

## 테스트

```sh
./scripts/test-native.sh
./scripts/test-schedule.sh
./scripts/test-v2.sh
# 실제 Android 뷰를 네이티브 Skia로 렌더링 (API 28, 음성 장치만 테스트 대체)
./gradlew :app:testStandardDebugUnitTest --tests pro.livemic.studio.StudioScreenshotTest
```

호스트 C++ 테스트는 음정 탐지, 무음 처리, MIDI 파일 구조, 링 버퍼, DSP의 유한 출력과 리버브 테일을 확인합니다. 장치 I/O와 카메라 테스트를 대체하지 않습니다.

## 사용 흐름

1. 유선 / USB 이어폰을 연결하거나 라이브 **소스 ▾ → 출력 장치**에서 휴대폰·블루투스 스피커를 선택합니다.
2. 마이크를 켜고 Android 녹음 권한을 허용합니다.
3. **상세 ▾**에서 보컬 프리셋을, **소스 ▾** 또는 반주 이름 행에서 반주를 선택합니다.
4. 오디오 녹음 또는 카메라 화면의 영상 녹화를 시작합니다.
5. 보관함에서 WAV·MID·MP4를 재생, 저장, 공유합니다.

## 반드시 구분할 사항

- 무지연을 보장하지 않습니다. 표시 값은 AAudio 출력 버퍼 길이이며 실제 왕복 지연 측정치가 아닙니다.
- 이어폰 모드에서는 유선/USB 출력이 필요합니다. 스피커 사용은 출력 모드에서 명시적으로 선택합니다.
- 블루투스 스피커 모드는 지연이 있습니다. 이어폰과 같은 저지연을 보장하지 않습니다.
- 배포용은 외부 앱 소리를 녹음하지 않습니다. 개인용은 Android의 공유 권한과 대상 앱의 캡처 허용이 필요하며, YouTube/SoundCloud 모든 콘텐츠 지원을 보장하지 않습니다.
- 카메라는 앱이 화면에 보일 때 사용하며, 화면을 벗어나면 영상을 저장하고 카메라를 해제합니다.
- 영상 저장 시 WAV를 AAC로 인코딩해 MP4에 결합합니다. 이 단계는 촬영 후에 진행되므로 저장을 기다려야 합니다.
