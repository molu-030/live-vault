# Live Vault 3.8 검증 기록

**3.8.0-preview / versionCode 18, personal 전용.** 3.7 체크포인트 `8eb991c` 위에 구현했다. 구현과 테스트 코드는 Sol (`gpt-6.1-sol`, medium)이 작성하고 루트가 소스·실행 결과·native Skia 화면을 직접 검토했다.

## 확인한 결과

- 개인용 전체 Java/Robolectric **110개, 실패·오류·생략 0**. [집계](validation/v3.8/full-java-counts.json).
- 이후 YouTube 화면의 중복 재생 줄을 제거하고 안내를 줄인 최종 변경에 대해 LiveConsole/YouTube 기능·화면 **33개, 실패·오류·생략 0**을 재검사하고 개인용 APK를 재빌드했다. [집계](validation/v3.8/final-ui-counts.json).
- `scripts/test-native.sh`, `scripts/test-v2.sh` 통과. 기존 44.1/48 kHz 음정·DSP·피드백·캡처 믹서와 녹음 정렬 검사를 수행했다. 새 마이크 전용 네이티브 녹음 분기는 소스 검토와 3 ABI 빌드로 확인했으며 실제 WAV/기기 검증을 대신하지 않는다.
- 앱이 실제 생성한 플레이어 HTML을 Chromium에서 실행해 **7개 항목**을 확인했다. YouTube API는 오프라인 대체 구현이며 실제 YouTube 서버/영상 재생 검증이 아니다. 공식 플레이어 옵션과 최소 영역, 음량/0% 무음, 숨김 시 차단과 자동 재개 방지, 문서 숨김, 오류 메시지 포트, 초기 네트워크 오류 보관·15초 준비 시간 초과·늦은 복구를 확인했다. [실행 결과](validation/v3.8/player-js.json).
- 한국어/영어 × 412×893 기본 글씨/360×800 130% 글씨의 네 조합에서 온라인·동의·라이브·440 Hz 녹음 fixture·오류, **20장**을 생성했다. [화면 렌더](screenshots/v3.8/). 플레이어 자리는 **OFFLINE PLAYER FIXTURE**로 명시한다. 실제 YouTube 영상이나 휴대폰 캡처가 아니다.

루트는 최종 한국어 412 기본 라이브, 영어 360 130% 녹음, 한국어 360 130% 동의 창을 직접 확인했다. 작은 LCD의 IN/OUT·A4·440 Hz·녹음 시계, 최소 200×200 플레이어 자리, 상세 효과 버튼과 고정 녹음 버튼의 겹침을 확인했다. 360 130%에서도 상세 효과 버튼까지 스크롤 없이 노출되도록 중복 네이티브 반주 재생 줄을 제거했다. 동의 창은 실제 dialog decorView와 배경을 렌더링한다.

## 회귀 검사 범위

공용 게인 조작 후 마이크 게인과 시스템 MUSIC/VOICE_CALL 음량 유지, URL/ID 검증, 동의 취소·실행 시 무단 플레이어 로드 방지, 녹음 중 동의 응답 재검사, 창 포커스·탭·백그라운드 일시정지와 자동 재생 방지, YouTube 녹음의 외부 캡처 해제와 늦은 권한 콜백 무시를 포함한다.

실제 서비스 RECORD 요청의 마이크 전용 플래그를 3초 카운트다운에 보관하고 플레이어 선택 상태가 바뀐 뒤에도 유지하는 것을 확인했다. 마이크 전용 녹음/예약 중 지연된 PLAYBACK 요청은 네이티브 재생을 시작하지 않고 foreground 기한과 현재 녹음 상태를 보존한다. 일반 파일·작곡기 녹음은 기존 믹스와 싱크를 사용한다.

## 개인용 APK 식별

- 앱 ID: `pro.livemic.studio.personal`
- 버전: `3.8.0-preview`, versionCode `18`
- 크기: `36,490,634 bytes`
- ABI: `arm64-v8a`, `armeabi-v7a`, `x86_64`
- APK SHA-256: `7a55639e62ac6f842b822cfae9e9687e9f8db4ed7b680eb8f015f69c7b683fd7`
- 서명 인증서 SHA-256: `6a61c39da7f13d3c7622c12068efd1e5bcb43bae2b7748821e2d2a9af169a139` — 제공된 기존 인증서와 일치.

개인용 APK·소스 오버레이·전달 URL과 키 파일은 공개 저장소에 포함하지 않는다. 기존 공개 `artifacts/Live-Vault-standard.apk`는 3.6.0-preview / code16 그대로이며 SHA-256은 `3ef89e3193c8235bc23080850b6eb06fc04ecba62ca35da20bfb89ac3b9e5732`다.

## 남은 검증과 정책 경계

실제 Android WebView에서 동영상별 임베드 허용·광고·네트워크 오류·독립 음량·유선/Bluetooth 경로, 마이크 합류/종료·실제 녹음의 유튜브 디지털 음원 제외·음향 유입, 터치/TalkBack, 장시간 사용은 실기기 확인이 필요하다. LCD OUT은 네이티브 출력만 측정하며 WebView의 YouTube 신호는 포함하지 않는다.

공식 플레이어·가시성·동의·마이크 전용 저장에 맞춰 구현했으나 YouTube 심사·승인을 받은 버전이라는 주장을 하지 않는다. 현재 자격 증명이 없는 YouTube Data API의 Made For Kids 상태 조회 및 추적 요건을 비롯한 공개 배포 전 정책 검토가 남아 있다. 개인정보·서비스 약관 고지를 포함한 [명세와 공식 정책 링크](LIVE_VAULT_V3_8_SPEC.md)를 참고한다.
