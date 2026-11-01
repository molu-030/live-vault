# Live Vault 3.6 검증 기록

개인용 3.6.0-preview / versionCode 16의 Java/Robolectric 테스트 87개가 통과했다 (실패 0, 오류 0, 생략 0). 44.1 kHz와 48 kHz 작곡기 네이티브 호스트 검사 및 실제 네이티브 소음 완화 켜짐/꺼짐 회귀 검사를 통과했다. 명시적인 마이크 장치 ID의 네이티브 전달 경로는 소스 검토와 컴파일로 확인했다. 실제 AAudio 장치 경로는 검증하지 못했다. 기능과 한국어 412 기본·360 130%, 영어 360 130% 라이브 및 설정 Skia 렌더를 직접 검토했다. 모니터 끄기·켜기 회귀 검사도 포함한다.

[화면 렌더](screenshots/v3.6/)에는 한국어·영어, 360×800 및 412×893, 기본 및 130% 글씨의 8개 조합과 6개 라이브 상태를 조합한 PNG 48장, 영어 412 기본 글씨의 입력·출력·효과 화면 3장이 있다. Android native Skia 테스트 렌더이며 상태와 오디오 입력은 테스트 데이터다. 실기기 캡처가 아니다.

실제 기기의 마이크·USB·Bluetooth SCO·BLE 스테레오와 AAudio 경로 열기, 지원되지 않는 경로의 오류 및 롤백은 검증하지 못했다. 장치 경로 변경 시 짧은 스트림 재시작이 발생한다. 공개 standard APK는 3.2.1-preview / versionCode 12로 유지되며 SHA-256은 `f84486dc562454b88f533ddd14fc6538fee8651974aff2737d0cb817991ff948`이다. 이번 3.6 빌드는 개인용이다.

최종 `:app:testPersonalDebugUnitTest :app:assemblePersonalDebug` 빌드가 통과했다. 개인용 APK 식별 검증: 앱 ID `pro.livemic.studio.personal`, 36,392,038 bytes, SHA-256 `793f4934f64d08623af0454b41afff36d35ccc676658cf21e7e31c9f06ba3a3f`, ABI arm64-v8a·armeabi-v7a·x86_64. 서명 인증서 SHA-256 `6a61c39da7f13d3c7622c12068efd1e5bcb43bae2b7748821e2d2a9af169a139`은 기존 인증서와 일치한다.
