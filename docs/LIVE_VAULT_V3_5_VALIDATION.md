# Live Vault 3.5 검증 기록

개인용 3.5.0-preview / versionCode 15의 최종 Gradle 빌드 및 전체 71개 테스트가 통과했다 (실패 0, 오류 0, 생략 0). 네이티브 호스트 검사는 44.1 kHz와 48 kHz에서 통과했으며 솔로 해제 시 믹서 보존과 파트 경계에서 음소거 복구를 포함한다. 대기 중인 재생 후 정지, 카운트인 취소 및 손상된 undo/redo 스택 보존 검사도 추가했다. 최종 기능 및 화면 검토는 직접 수행했다. 한국어 412 기본 글씨와 영어 360 130% 글씨의 라이브·반주 렌더를 직접 확인했다.

[화면 렌더](screenshots/v3.5/)에는 한국어·영어, 360×800 및 412×893, 기본 및 130% 글씨의 8개 조합과 대기·딜레이·목소리·녹음·일시정지·보호 음소거 6개 상태를 조합한 라이브 PNG 48장이 있다. 반주 화면 PNG는 현재 ComposerRedesignScreenshotTest의 렌더를 별도 이름으로 보관한다. 화면 검사는 48dp 터치 대상, 공용 다이얼 하나, 라이브 본문의 스크롤 없음과 모든 값 버튼의 최대값이 잘리지 않는지를 확인한다. 영어 360 130% 화면의 Voice 200% 표시도 직접 확인했다. Android native Skia를 사용한 테스트 렌더이며 상태와 오디오 입력은 테스트 데이터다. 실기기 캡처가 아니다.

실제 마이크, Bluetooth 및 기기 동작은 이 환경에서 검증하지 못했다. 공개 standard APK는 3.2.1-preview / versionCode 12로 유지되며 SHA-256은 `f84486dc562454b88f533ddd14fc6538fee8651974aff2737d0cb817991ff948`이다. 이번 3.5 빌드는 개인용이다.

최종 Android 검증 명령: `:app:testPersonalDebugUnitTest :app:assemblePersonalDebug`.

개인용 APK 식별 검증: 앱 ID `pro.livemic.studio.personal`, 36,404,894 bytes, SHA-256 `c04258663e2fc73063622cb95afc6b0b1f82dbc06f5ac06b30cfe067dfd95eec`, ABI arm64-v8a·armeabi-v7a·x86_64. 서명 인증서 SHA-256 `6a61c39da7f13d3c7622c12068efd1e5bcb43bae2b7748821e2d2a9af169a139`은 기존 인증서와 일치한다.
