package pro.livemic.studio;

import android.content.Context;
import java.util.*;

final class InstrumentCatalog {
    static final String[] CATEGORIES={"피아노","크로매틱 타악기","오르간","기타","베이스","현악","앙상블","금관","리드","파이프","신스 리드","신스 패드","신스 효과","민속 악기","타악기","효과음"};
    static final String[] GM=("Acoustic Grand Piano|Bright Piano|Electric Grand Piano|Honky-tonk Piano|Electric Piano 1|Electric Piano 2|Harpsichord|Clavinet|Celesta|Glockenspiel|Music Box|Vibraphone|Marimba|Xylophone|Tubular Bells|Dulcimer|Drawbar Organ|Percussive Organ|Rock Organ|Church Organ|Reed Organ|Accordion|Harmonica|Tango Accordion|Nylon Guitar|Steel Guitar|Jazz Guitar|Clean Guitar|Muted Guitar|Overdrive Guitar|Distortion Guitar|Guitar Harmonics|Acoustic Bass|Finger Bass|Pick Bass|Fretless Bass|Slap Bass 1|Slap Bass 2|Synth Bass 1|Synth Bass 2|Violin|Viola|Cello|Contrabass|Tremolo Strings|Pizzicato Strings|Orchestral Harp|Timpani|String Ensemble 1|String Ensemble 2|Synth Strings 1|Synth Strings 2|Choir Aahs|Voice Oohs|Synth Voice|Orchestra Hit|Trumpet|Trombone|Tuba|Muted Trumpet|French Horn|Brass Section|Synth Brass 1|Synth Brass 2|Soprano Sax|Alto Sax|Tenor Sax|Baritone Sax|Oboe|English Horn|Bassoon|Clarinet|Piccolo|Flute|Recorder|Pan Flute|Blown Bottle|Shakuhachi|Whistle|Ocarina|Square Lead|Saw Lead|Calliope Lead|Chiff Lead|Charang Lead|Voice Lead|Fifths Lead|Bass and Lead|New Age Pad|Warm Pad|Polysynth Pad|Choir Pad|Bowed Pad|Metallic Pad|Halo Pad|Sweep Pad|Rain|Soundtrack|Crystal|Atmosphere|Brightness|Goblins|Echoes|Sci-fi|Sitar|Banjo|Shamisen|Koto|Kalimba|Bagpipe|Fiddle|Shanai|Tinkle Bell|Agogo|Steel Drums|Woodblock|Taiko Drum|Melodic Tom|Synth Drum|Reverse Cymbal|Guitar Fret Noise|Breath Noise|Seashore|Bird Tweet|Telephone Ring|Helicopter|Applause|Gunshot").split("\\|");
    static final String[] KO=("어쿠스틱 그랜드 피아노|브라이트 피아노|일렉트릭 그랜드 피아노|헝키통크 피아노|일렉트릭 피아노 1|일렉트릭 피아노 2|하프시코드|클라비넷|첼레스타|글로켄슈필|뮤직박스 (오르골)|비브라폰|마림바|실로폰|튜뷸러 벨|덜시머|드로우바 오르간|퍼커시브 오르간|록 오르간|파이프 오르간|리드 오르간|아코디언|하모니카|탱고 아코디언|나일론 기타|스틸 기타|재즈 기타|클린 일렉기타|뮤트 기타|오버드라이브 기타|디스토션 기타|기타 하모닉스|어쿠스틱 베이스|핑거 베이스|픽 베이스|프렛리스 베이스|슬랩 베이스 1|슬랩 베이스 2|신스 베이스 1|신스 베이스 2|바이올린|비올라|첼로|콘트라베이스|트레몰로 현악|피치카토 현악|하프|팀파니|현악 앙상블 1|현악 앙상블 2|신스 스트링 1|신스 스트링 2|합창 (아)|목소리 (오)|신스 보이스|오케스트라 히트|트럼펫|트롬본|튜바|뮤트 트럼펫|프렌치 호른|브라스 섹션|신스 브라스 1|신스 브라스 2|소프라노 색소폰|알토 색소폰|테너 색소폰|바리톤 색소폰|오보에|잉글리시 호른|바순|클라리넷|피콜로|플루트|리코더|팬플루트|블로운 보틀|샤쿠하치|휘슬|오카리나|스퀘어 리드|소우 리드|칼리오페 리드|치프 리드|차랑 리드|보이스 리드|5도 리드|베이스 앤 리드|뉴에이지 패드|웜 패드|폴리신스 패드|합창 패드|보우 패드|메탈릭 패드|헤일로 패드|스윕 패드|빗소리|사운드트랙|크리스탈|앳모스피어|브라이트니스|고블린|에코 드롭스|공상과학 (Sci-Fi)|시타르|밴조|샤미센|코토|칼림바|백파이프|피들|샤나이|팅클 벨|아고고|스틸 드럼|우드블록|태고 드럼|멜로딕 탐|신스 드럼|리버스 심벌|기타 프렛 노이즈|숨소리|파도 소리|새소리|전화벨|헬리콥터|박수|총소리").split("\\|");
    static final int[] FEATURED={24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39,0,4,16,40,42,65,73,89};

    static String displayName(Context c, int program) {
        if (program < 0 || program >= GM.length) return "";
        return AppLanguage.english(c) ? GM[program] : KO[program] + " (" + GM[program] + ")";
    }

    static String[] categories(Context c){
        return AppLanguage.english(c)?new String[]{"Piano","Chromatic percussion","Organ","Guitar","Bass","Strings","Ensemble","Brass","Reed","Pipe","Synth lead","Synth pad","Synth effects","Ethnic","Percussive","Sound effects"}:CATEGORIES;
    }
    static String[] patternLabels(Context c,TrackConfig t){
        if(!AppLanguage.english(c))return patterns(t);
        if(t.drums())return new String[]{"Standard rhythm","Hi-hat light","Kick driving","Ride cymbal groove","Minimal rimshot"};
        if(t.family()==3)return new String[]{"Strum","Arpeggio","Muted strum","Power chord","16-beat pop strum","Lo-Fi jazz loop","Ballad fingerstyle"};
        if(t.family()==4)return new String[]{"Root","Root · fifth","Octave","Walking","Slap & pop","8-beat driving","808 deep sustain"};
        if(t.family()==7||t.family()==8||t.family()==9||t.family()==10)return new String[]{"Chord-tone melody","Long notes","Staccato riff","Synth pulse","Octave unison"};
        return new String[]{"Block chords","Arpeggio","Sustained chords","Pop arpeggio","Syncopated bounce","Rhythm comping","Swing comping"};
    }
    static String[] patterns(TrackConfig t){
        if(t.drums())return new String[]{"기본 리듬 패턴","하이햇 중심 라이트","킥 드라이빙 그루브","라이드 심벌 그루브","미니멀 림샷"};
        if(t.family()==3)return new String[]{"스트럼","아르페지오","커팅","파워코드","16비트 팝 스트럼","재즈/로우파이 룹","발라드 핑거스타일"};
        if(t.family()==4)return new String[]{"근음","근음 · 5도","옥타브","워킹","슬랩 & 팝","8비트 드라이빙","808 딥 서스테인"};
        if(t.family()==7||t.family()==8||t.family()==9||t.family()==10)return new String[]{"코드톤 멜로디","롱톤","스타카토 리프","신스 펄스","옥타브 유니즌"};
        return new String[]{"블록 코드","아르페지오","지속 코드","팝 아르페지오","싱코페이션 반주","리듬 콤핑","스윙 컴핑"};
    }
}
