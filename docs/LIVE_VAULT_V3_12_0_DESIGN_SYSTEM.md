# 3.12.0-preview — 공통 디자인 규칙

사용자가 지정한 [Material Design 3](https://m3.material.io/)의 레이아웃·타입·접근성 원칙을 바탕으로 기존 매트 스튜디오 테마를 정리한다. Material 라이브러리로 화면을 전면 교체하거나 공식 규격 인증을 주장하는 변경은 아니다.

## 공통 규칙

| 역할 | 규칙 |
|---|---|
| 간격 | 4·8·12·16·24·32dp 토큰으로 관리 |
| 페이지 좌우 | 폰 16dp / 확장 태블릿 24dp, 시스템 안전 영역 별도 |
| 카드 | 안쪽 16dp, 카드 사이 16dp, 모서리 12dp |
| 섹션 | 서로 다른 섹션 24dp / 관련 조작 8dp |
| 버튼 | 최소 48dp 터치, 좌우 16dp·상하 12dp 기본 여백, 모서리 12dp |
| 보조 글자 | 12sp / 줄높이 16sp |
| 본문·버튼 | 14sp / 줄높이 20sp |
| 소제목 | 16sp / 줄높이 24sp |
| 앱 제목 계층 | 20/28sp, 24/32sp |

같은 역할의 조작부는 같은 여백과 최소 높이를 사용하고 긴 번역은 줄바꿈과 자연 높이를 허용한다. 서로 다른 콘텐츠의 카드 높이를 강제로 동일하게 만들지 않는다. 폰 라이브의 고정 조작·YouTube 플레이어 영역은 4dp 격자의 컴팩트 간격을 사용한다. 이때도 48dp 터치와 12sp 이상 라벨을 유지한다.

기존 라임 LCD·어두운 매트 색·큰 효과 노브를 유지한다. 버튼의 둥근 사각형은 기존 승인 테마에 맞춘 앱 형태다. Material 3 기본 버튼의 완전한 캡슐 모양을 일괄 적용하지 않는다.

## 적용 범위

공통 StudioTheme/StudioButton/StudioFader와 페이지 뷰포트, 라이브·트랙 믹서, 작곡기·화음 화면, 보관함·설정·팝업·후편집의 간격·타입·동급 조작 크기를 정리한다. 오디오 버스·프로젝트 파일·녹음 처리에는 변경을 가하지 않는다.

## 참조와 검증

- [Material 3 Grids & spacing](https://m3.material.io/foundations/layout/understanding-layout/spacing)
- [공식 Material Web 타입 토큰](https://github.com/material-components/material-web/blob/main/tokens/versions/v0_192/_md-sys-typescale.scss): body-medium 14/20, label-large 14/20, title-medium 16/24를 참조한다.
- [공식 버튼 토큰](https://github.com/material-components/material-web/blob/main/tokens/versions/v0_192/_md-comp-filled-button.scss): 시각 크기와 터치 영역을 구분하며 앱은 기존 48dp 최소 조작을 유지한다.

검증 결과와 실제 Android 뷰/Skia 렌더는 `validation/v3.12.0/`에 기록한다. 합성 오디오 상태·프로젝트 fixture를 사용한 화면 검사이며 실기기 음향·배터리 측정은 사용자의 요청에 따라 보류 상태다.

최종 고유 Java/UI 검사 326개 통과. 전체 실행의 작은 창 레이아웃 2건을 수정하고 관련 51개를 재검증했다. 그 과정의 재생 테스트 2건은 이전 테스트의 Composer 재생 상태가 남은 문제로 격리 초기화를 추가한 뒤 30개를 재실행해 통과했다. 실행별 원시 집계는 java-tests.json에 보존한다. 개인용 APK의 기존 서명·패키지·버전을 확인했다.
