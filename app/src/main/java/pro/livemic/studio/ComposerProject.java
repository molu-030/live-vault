package pro.livemic.studio;

import org.json.*;
import java.util.*;

final class ComposerProject {
    static final int VERSION=1,MAX_TRACKS=8,MAX_PARTS=8,MAX_BARS=256;
    String id=UUID.randomUUID().toString(),name="새 반주";int key=0,scale=0,bpm=90,meter=0;long seed=481516;
    final List<Part> parts=new ArrayList<>();final List<Order> order=new ArrayList<>();
    VoiceInstrumentSettings voice=new VoiceInstrumentSettings();
    static final class Chord {
        int root,quality,bass=-1,degree=0;boolean automatic=true,seventh=false,dominant=false;
        void resolve(ComposerProject p){if(automatic){root=MusicTheory.pc(p.key+MusicTheory.SCALE[p.scale][degree]);quality=dominant?6:MusicTheory.quality(p.scale,degree,seventh);}}
        String label(ComposerProject p){
            int displayDegree=degree;String accidental="";
            if(!automatic){int distance=99;for(int d=0;d<7;d++){int delta=Math.floorMod(root-p.key-MusicTheory.SCALE[p.scale][d]+6,12)-6;if(Math.abs(delta)<distance){distance=Math.abs(delta);displayDegree=d;accidental=delta<0?"♭":delta>0?"♯":"";}}}
            String roman=new String[]{"I","II","III","IV","V","VI","VII"}[displayDegree];
            if(quality==1||quality==8||quality==2||quality==9)roman=roman.toLowerCase(Locale.ROOT);
            if(quality==2)roman+="°";if(quality==9)roman+="ø";if(seventh||quality>=6&&quality<=9)roman+="7";
            return MusicTheory.ROOTS[root]+MusicTheory.QUALITIES[quality]+(bass>=0?"/"+MusicTheory.ROOTS[bass]:"")+" · "+accidental+roman+(automatic?"":" 직접");
        }
        JSONObject json()throws JSONException{return new JSONObject().put("root",root).put("quality",quality).put("bass",bass).put("degree",degree).put("automatic",automatic).put("seventh",seventh).put("dominant",dominant);}
        static Chord read(JSONObject j){Chord c=new Chord();c.root=MusicTheory.pc(j.optInt("root"));c.quality=Math.max(0,Math.min(10,j.optInt("quality")));c.bass=j.optInt("bass",-1);if(c.bass>=0)c.bass=MusicTheory.pc(c.bass);c.degree=Math.floorMod(j.optInt("degree"),7);c.automatic=j.optBoolean("automatic",true);c.seventh=j.optBoolean("seventh");c.dominant=j.optBoolean("dominant");return c;}
    }
    static final class Part {
        String id=UUID.randomUUID().toString(),label="A";int bars=8,rhythm=0;float energy=.55f,humanize=.65f;boolean fill=true;
        static float unit(JSONObject j,String key,float fallback){double value=j.optDouble(key,fallback);return Double.isFinite(value)?(float)Math.max(0,Math.min(1,value)):fallback;}
        final List<List<Chord>> chords=new ArrayList<>();final List<TrackConfig> tracks=new ArrayList<>();final List<VoicePhrase> phrases=new ArrayList<>();
        JSONObject json()throws JSONException{JSONArray c=new JSONArray(),t=new JSONArray(),v=new JSONArray();for(List<Chord> bar:chords){JSONArray a=new JSONArray();for(Chord ch:bar)a.put(ch.json());c.put(a);}for(TrackConfig tr:tracks)t.put(tr.json());for(VoicePhrase ph:phrases)v.put(ph.json());return new JSONObject().put("id",id).put("label",label).put("bars",bars).put("rhythm",rhythm).put("energy",energy).put("humanize",humanize).put("fill",fill).put("chords",c).put("tracks",t).put("phrases",v);}
        static Part read(JSONObject j)throws JSONException{Part p=new Part();p.id=j.getString("id");p.label=j.getString("label");p.bars=j.getInt("bars");if(p.bars!=4&&p.bars!=8&&p.bars!=16)throw new JSONException("파트 길이");p.rhythm=Math.max(0,Math.min(MusicTheory.RHYTHMS.length-1,j.optInt("rhythm")));p.energy=unit(j,"energy",.55f);p.humanize=unit(j,"humanize",.65f);p.fill=j.optBoolean("fill",true);JSONArray c=j.getJSONArray("chords"),t=j.getJSONArray("tracks"),v=j.optJSONArray("phrases");if(c.length()!=p.bars||t.length()>8)throw new JSONException("파트 데이터");for(int b=0;b<c.length();b++){JSONArray a=c.getJSONArray(b);if(a.length()<1||a.length()>2)throw new JSONException("마디 코드 수");List<Chord> bar=new ArrayList<>();for(int i=0;i<a.length();i++)bar.add(Chord.read(a.getJSONObject(i)));p.chords.add(bar);}for(int i=0;i<t.length();i++)p.tracks.add(TrackConfig.read(t.getJSONObject(i)));if(v!=null)for(int i=0;i<v.length();i++)p.phrases.add(VoicePhrase.read(v.getJSONObject(i)));return p;}
    }
    static final class Order {String part;int repeats;Order(String p,int r){part=p;repeats=r;}}
    ComposerProject(){Part p=new Part();p.tracks.add(new TrackConfig("기타",24,false));p.tracks.add(new TrackConfig("베이스",33,false));TrackConfig d=new TrackConfig("드럼",0,false);d.bank=128;p.tracks.add(d);p.tracks.add(new TrackConfig("키보드",0,true));parts.add(p);order.add(new Order(p.id,1));preset(p,0);}
    Part part(String id){for(Part p:parts)if(p.id.equals(id))return p;throw new IllegalArgumentException("없는 파트");}
    int totalBars(){int n=0;for(Order o:order)n+=part(o.part).bars*o.repeats;return n;}
    void validate(){if(parts.isEmpty()||parts.size()>8||order.isEmpty()||order.size()>64||totalBars()>256)throw new IllegalArgumentException("파트 최대 8개 · 곡 최대 256마디");Set<String> ids=new HashSet<>();List<String> tracks=new ArrayList<>();for(Part p:parts){if(!ids.add(p.id)||p.tracks.size()>8)throw new IllegalArgumentException("중복 파트 / 트랙 한도");for(TrackConfig t:p.tracks)if(!tracks.contains(t.id))tracks.add(t.id);}if(tracks.size()>8)throw new IllegalArgumentException("악기는 최대 8트랙입니다.");for(Order o:order)if(o.repeats<1||o.repeats>64)throw new IllegalArgumentException("반복 횟수");}
    void transpose(int next){int delta=MusicTheory.pc(next)-key;for(Part p:parts){for(List<Chord> bar:p.chords)for(Chord c:bar){c.root=MusicTheory.pc(c.root+delta);if(c.bass>=0)c.bass=MusicTheory.pc(c.bass+delta);}for(VoicePhrase v:p.phrases)v.transpose(delta);}key=MusicTheory.pc(next);}
    void scale(int next){scale=next;for(Part p:parts)for(List<Chord> bar:p.chords)for(Chord c:bar)c.resolve(this);}
    void meter(int next){meter=next;for(Part p:parts)if(!MusicTheory.rhythmFits(p.rhythm,meter))p.rhythm=meter==0?0:meter==1?6:7;}
    void preset(Part p,int index){if(index>=4&&(scale==0||scale==4))scale(1);if(index==2&&p.bars==4)throw new IllegalArgumentException("캐논은 8·16마디에서 선택하세요.");p.chords.clear();int[] progression=MusicTheory.PROGRESSIONS[index];for(int i=0;i<p.bars;i++){Chord c=new Chord();c.degree=progression[i%progression.length];c.seventh=index==3;c.dominant=(index==3&&c.degree==4)||(index==5&&c.degree==4);if(c.dominant)c.seventh=true;c.resolve(this);p.chords.add(new ArrayList<>(Collections.singleton(c)));}}
    void resize(Part p,int bars){if(bars!=4&&bars!=8&&bars!=16)throw new IllegalArgumentException("파트 길이");int old=p.bars;p.bars=bars;if(totalBars()>256){p.bars=old;throw new IllegalArgumentException("곡 최대 256마디");}try{while(p.chords.size()<bars){List<Chord> copy=new ArrayList<>();for(Chord c:p.chords.get(p.chords.size()%old))copy.add(Chord.read(c.json()));p.chords.add(copy);}while(p.chords.size()>bars)p.chords.remove(p.chords.size()-1);}catch(JSONException e){throw new IllegalStateException(e);}}
    Part duplicate(Part source)throws JSONException{if(parts.size()>=8)throw new IllegalArgumentException("파트 최대 8개");Part p=Part.read(source.json());p.id=UUID.randomUUID().toString();Set<String> labels=new HashSet<>();for(Part x:parts)labels.add(x.label);for(char c='A';c<='H';c++)if(!labels.contains(""+c)){p.label=""+c;break;}parts.add(p);return p;}
    void addTrack(TrackConfig t){Set<String> ids=new HashSet<>();for(Part p:parts){if(p.tracks.size()>=MAX_TRACKS)throw new IllegalArgumentException("악기는 최대 8트랙입니다.");for(TrackConfig track:p.tracks)ids.add(track.id);}if(!ids.contains(t.id)&&ids.size()>=MAX_TRACKS)throw new IllegalArgumentException("악기는 최대 8트랙입니다.");try{for(Part p:parts)p.tracks.add(TrackConfig.read(t.json()));}catch(JSONException e){throw new IllegalStateException(e);}}
    JSONObject json()throws JSONException{JSONArray p=new JSONArray(),o=new JSONArray();for(Part part:parts)p.put(part.json());for(Order entry:order)o.put(new JSONObject().put("part",entry.part).put("repeats",entry.repeats));return new JSONObject().put("version",VERSION).put("id",id).put("name",name).put("key",key).put("scale",scale).put("bpm",bpm).put("meter",meter).put("seed",seed).put("parts",p).put("order",o).put("voice",voice.json());}
    static ComposerProject read(JSONObject j)throws JSONException{if(j.getInt("version")!=VERSION)throw new JSONException("지원하지 않는 프로젝트 버전");ComposerProject p=new ComposerProject();p.id=j.getString("id");p.name=j.getString("name");p.key=MusicTheory.pc(j.getInt("key"));p.scale=Math.max(0,Math.min(4,j.getInt("scale")));p.bpm=Math.max(40,Math.min(240,j.getInt("bpm")));p.meter=Math.max(0,Math.min(2,j.getInt("meter")));p.seed=j.optLong("seed",481516);p.voice=VoiceInstrumentSettings.read(j.optJSONObject("voice"));p.parts.clear();p.order.clear();JSONArray a=j.getJSONArray("parts"),o=j.getJSONArray("order");if(a.length()>8||o.length()>64)throw new JSONException("프로젝트 크기");for(int i=0;i<a.length();i++)p.parts.add(Part.read(a.getJSONObject(i)));for(int i=0;i<o.length();i++){JSONObject v=o.getJSONObject(i);p.order.add(new Order(v.getString("part"),v.getInt("repeats")));}try{p.validate();}catch(IllegalArgumentException e){throw new JSONException(e.getMessage());}return p;}
}
