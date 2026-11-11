# 오디오 SDK 검토 — 2026-11-09

## Oboe

[Google Oboe](https://github.com/google/oboe)는 Android 저지연 입출력 래퍼이며 현재 프로젝트의 AAudio 입출력과 같은 층에 해당한다. [Apache 2.0](https://github.com/google/oboe/blob/main/LICENSE)으로 상업용 사용·배포가 가능하고 라이선스/저작권 고지 및 해당 NOTICE·변경 고지 의무를 준수해야 한다. Oboe만 도입해서 피치 보정이나 하울링 제거가 자동으로 생기지는 않는다. 현재 앱의 지원 범위는 이미 AAudio를 사용할 수 있으므로 교체만으로 지연이 줄었다고 판단해서는 안 된다.

자체 보컬 피치 보정은 `클린 입력 → 유성음/기본 주파수 검출 → 키·스케일의 목표음 선택 → 보정 속도·허용폭·비브라토 보존 → 포먼트를 고려한 피치 시프터 → 기존 효과/믹서`로 분리한다. 발음 경계·자음·무음·옥타브 오류 처리와 드라이/보정 경로의 지연 정렬이 필요하다. 오디오 콜백에서 파일 I/O·메모리 할당·Java/UI 호출을 하지 않는다. 블록 크기만으로 실제 왕복 지연을 추정하지 않고 실기기에서 측정한다.

## Superpowered

[공식 AutomaticVocalPitchCorrection 헤더](https://github.com/superpoweredSDK/Low-Latency-Android-iOS-Linux-Windows-tvOS-macOS-Interactive-Audio-Platform/blob/master/Superpowered/SuperpoweredAutomaticVocalPitchCorrection.h)에서 자동 보컬 피치 보정 기능을 확인했다. 타임 스트레칭·리버브·딜레이·믹싱 같은 DSP를 제공하는 상용 SDK 범주이나 신규 도입 판단은 아래 종료 공지가 우선이다.

[공식 종료 공지](https://superpowered.com/superpowered-farewell): SDK는 deprecated이며 **2027-07-31 운영 종료**, **신규 라이선스 발급 중단**, 기존 계약은 비례 갱신만 제공한다고 명시한다. 새 Live Vault 의존성으로 채택하지 않는 것을 권한다. 공지는 Yuby를 안내하지만 Yuby의 라이선스·API·가격은 별도 확인 대상이며 Superpowered와 동일하다고 가정하지 않는다.

[Master License Agreement](https://superpowered.com/licensing)(페이지의 발효일 2022-03-23) 및 [가격 안내](https://superpowered.com/pricing)에서 확인한 조건:

- 오픈소스가 아니며 무료 공개 배포 라이선스가 없다.
- Evaluation은 비공개 실험·R&D에 한정되고 1,000 설치 기준 제한과 사용 중단 조건이 있다. 개인용 APK라는 이유만으로 무제한 상시 사용이 허용된다고 단정할 수 없다.
- White Label은 앱별 연간 비용 계약이며 무제한 설치/로열티 없음으로 안내한다. 공개된 확정 금액은 확인하지 않았으며 신규 계약 중단 공지를 함께 적용해야 한다.
- 라이선스 키 검증·사용량 모니터링 조건이 있다. 네트워크나 오프라인 운용 조건은 계약 확인 없이 보장하지 않는다.
- 오픈소스 저장소에 포함하는 경우에도 별도 약관, README 언급 및 약관 사본 조건 등이 있다. 단순히 소스가 공개되어 있다고 SDK 바이너리를 자유 배포할 수 있는 것은 아니다.

이 검토로 SDK를 다운로드해 앱에 통합하거나 계약·구매·계정 등록을 진행하지 않았다.

## LCD 파형 제안

입력 미터 아래 24~32dp 높이의 단색 파형을 두고 피치·녹음 시간·경고보다 낮은 시각적 우선순위로 둔다. 정지/무음에는 정적인 선을 표시하고 20~30fps로만 갱신하며 화면이 보이지 않으면 갱신을 멈춘다. 클린 마이크의 다운샘플된 min/max envelope를 사전 할당된 링 버퍼로 전달한다. 원음 파일을 만들지 않으며 디지털 반주와 수음 파형을 혼동하지 않도록 입력 파형으로 표시한다. 모양·설계 제안이며 이번 디자인 통일 작업에 파형이나 새 피치 보정 SDK를 통합하지는 않았다.
