# 오프라인 샘플 음원과 합성기

## GeneralUser GS SoftSynth 1.44

- 제작: S. Christian Collins, <https://www.schristiancollins.com/generaluser>
- 공식 Legacy 배포의 **GeneralUser GS SoftSynth version 1.44**에서 SF2/README/CHANGELOG/LICENSE를 변경 없이 추출했다. 데모 MIDI는 포함하지 않았다.
- 파일: `app/src/main/assets/soundfonts/generaluser-gs-1.44.sf2`, 31,279,314 bytes.
- SHA-256: `00725296486f652ba42bdbf2098254762ca80239353d04b55a27454fc8edb8a8`.
- 원본 문서: `app/src/main/assets/licenses/GeneralUser-1.44-{LICENSE,README,CHANGELOG}.txt`.
- 앱은 포함한 음원을 로컬 파일로 초기화하며 연주 중 네트워크 접근이 필요하지 않다.

## TinySoundFont

- <https://github.com/schellingb/TinySoundFont>, 체크아웃 시 HEAD `853a0a171759f1ddba0de1442133a75912bbeffa`.
- `tsf.h`와 원본 MIT 라이선스를 `app/src/main/cpp/vendor`에 포함한다. 3.9.3에서 `GEN_UINT_ADD15`의 음수 signed left shift 한 곳을 동등한 signed 곱셈으로 수정했다. 원래 short 범위에서 32비트 int 곱셈이 안전하며 unsigned 주소 누산은 유지한다. 원본 음원 데이터는 변경하지 않았다.
- APK에도 `assets/licenses/TinySoundFont-LICENSE.txt`를 포함한다.
- TSF 기본 모듈레이터 구현의 범위 안에서 사용하는 샘플 프리셋이다. 하드웨어 GS 장치와 음색이 완전히 동일하다는 주장은 하지 않는다.
