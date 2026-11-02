# Live Vault 3.8 — 화면 안의 YouTube 연습 재생

3.8.0-preview / versionCode 18, 개인용 APK만 전달한다. 기존 앱 ID와 제공된 개발 서명을 유지한다. Sol (`gpt-6.1-sol`, medium)이 구현하고 루트가 직접 검토한다.

## 사용 흐름

반주 → 온라인에서 HTTPS YouTube 동영상 주소 또는 11자리 동영상 ID를 입력하고 앱에서 선택한다. 처음에는 Google/YouTube 연결·기기 정보·쿠키/저장소 및 약관을 안내하고 동의를 받은 뒤 플레이어를 불러온다. 검색은 기존 외부 YouTube 열기를 이용한다. 앱 실행 시 동영상을 자동으로 불러오거나 재생하지 않는다.

선택한 동영상은 라이브 화면의 공식 IFrame 플레이어에서 사용자가 재생한다. 기존 반주 음량은 네이티브 반주 게인과 YouTube `setVolume(0..100)`에 연결한다. 휴대폰 전체 스트림 음량과 마이크 게인을 변경하지 않는다. 다른 앱에서 재생하는 YouTube의 음량을 제어하는 기능은 아니다.

동영상은 최소 200×200 CSS 픽셀의 보이는 영역을 확보한다. YouTube 선택 시 LCD를 줄이고 보컬 효과 슬라이더는 상세 창으로 옮겨 공간을 확보한다. 탭 이동, 백그라운드, 플레이어가 보이지 않는 스크롤 상태에서는 일시정지하고 자동 재개하지 않는다. 파일·작곡기·화음 루프와의 재생 소유권을 교체한다.

## 녹음 분리

YouTube 선택 시에는 마이크만 녹음한다. 다른 앱 소리 캡처 선택을 해제하고 공유 권한을 요청하지 않는다. 녹음 요청의 마이크 전용 플래그는 서비스의 카운트다운에 보관해 화면 재생성에도 유지한다. 네이티브 녹음 버스는 처리된 마이크 신호만 받고 반주·음성 악기·화음 루프·외부 캡처 버스를 제외한다. 파일/작곡기 반주의 기존 녹음 믹스는 유지한다.

스피커에서 나온 소리가 실제 마이크에 다시 들어가는 음향 유입까지 차단한다는 보장은 없다. 따라서 플레이어 옆에 이어폰 권장 안내를 표시한다. 유튜브 소리를 녹음·추출·다운로드하는 기능을 제공하지 않는다. 라이브 LCD의 OUT 미터는 네이티브 오디오 출력만 측정하며 별도 WebView에서 재생되는 YouTube 신호는 측정하지 않는다.

## 정책 및 보안 경계

- 공식 플레이어와 문서화된 API만 사용하며 광고·로고·플레이어 링크를 차단하지 않는다.
- 앱 ID의 HTTPS origin/referrer를 제공한다. 영상 ID는 허용한 HTTPS YouTube 호스트와 경로에서만 추출한다. 파일 접근·콘텐츠 접근·혼합 콘텐츠는 끈다.
- 임의 콘텐츠에 Android Java 객체를 노출하지 않는다. 오류 보고는 앱 origin에 연결한 메시지 포트의 정해진 숫자 코드로 제한한다.
- 개인정보·약관 안내와 공식 문서 준수 설계는 YouTube의 심사·승인을 받았다는 뜻이 아니다. 공개 배포 전 개인정보 고지, 아동용 콘텐츠에 관한 Made For Kids 상태 조회 및 추적 요건을 포함해 정책을 다시 검토해야 한다. 현재 환경에는 YouTube Data API 자격 증명이 없으며 상태 조회 검증은 완료되지 않았다.

공식 근거: [IFrame API](https://developers.google.com/youtube/iframe_api_reference), [임베드 최소 요구사항](https://developers.google.com/youtube/terms/required-minimum-functionality), [개발자 정책](https://developers.google.com/youtube/terms/developer-policies), [정책 설명](https://developers.google.com/youtube/terms/developer-policies-guide), [Made For Kids 조회](https://developers.google.com/youtube/data/guides/made_for_kids_status).

## 검증 구분

Java/Robolectric 검사는 WebView 경계를 대체하며 실제 YouTube 재생을 증명하지 않는다. 화면 렌더의 동영상 자리는 OFFLINE fixture로 명시한다. 실제 Android WebView의 동영상별 임베드 허용, 광고·네트워크·음량·백그라운드 전환과 유선/Bluetooth 출력은 실기기에서 별도로 확인해야 한다. 최종 검사 결과와 APK 식별은 검증 문서에 기록한다.
