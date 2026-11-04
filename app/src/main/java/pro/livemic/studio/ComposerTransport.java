package pro.livemic.studio;
import java.util.*;

/** Expands harmony and seeded articulation on the worker. No project parsing in AAudio. */
final class ComposerTransport {
    static final int STRIDE=9;
    static final class Plan {double[] events;double beats,bar,bpm;int key,scale;}
    private final List<double[]> events=new ArrayList<>();
    private Random random;private double bar;private ComposerProject project;
    private final double[][] pendingOff=new double[8][128],pendingOn=new double[8][128];
    private final Map<String,int[]> previousVoicings=new HashMap<>();
    private final double[][][] offEvent=new double[8][128][];
    private void event(double beat,int type,int track,int note,double velocity,int program,int bank,double pan,double volume){events.add(new double[]{beat,type,track,note,velocity,program,bank,pan,volume});}
    private void note(double beat,double length,int ch,int pitch,double velocity,boolean human){double offset=human?random.nextDouble()*.018:0;pitch=Math.max(0,Math.min(127,pitch));event(beat+offset,1,ch,pitch,Math.max(.05,Math.min(1,velocity+(human?(random.nextDouble()-.5)*.09:0))),0,0,0,0);event(beat+offset+Math.max(.03,length),0,ch,pitch,0,0,0,0,0);}
    Plan compile(ComposerProject p,int selected){project=p;p.validate();events.clear();random=new Random(p.seed);for(int ch=0;ch<8;ch++)Arrays.fill(offEvent[ch],null);previousVoicings.clear();bar=MusicTheory.barBeats(p.meter);double beat=0;
        List<Integer> sequence=new ArrayList<>();
        if(selected>=0)sequence.add(selected);
        else for(ComposerProject.Order o:p.order){int index=p.parts.indexOf(p.part(o.part));for(int r=0;r<o.repeats;r++)sequence.add(index);}
        for(int i=0;i<sequence.size();i++){
            int index=sequence.get(i);ComposerProject.Part nextPart=p.parts.get(sequence.get((i+1)%sequence.size()));
            beat=part(p.parts.get(index),index,beat,nextPart.chords.get(0).get(0));
        }
        events.sort((a,b)->{int c=Double.compare(a[0],b[0]);return c!=0?c:Integer.compare(rank((int)a[1]),rank((int)b[1]));});
        Plan result=new Plan();result.events=new double[events.size()*STRIDE];for(int i=0;i<events.size();i++)System.arraycopy(events.get(i),0,result.events,i*STRIDE,STRIDE);result.beats=beat;result.bar=bar;result.bpm=MusicTheory.quarterBpm(p.bpm,p.meter);result.key=p.key;result.scale=p.scale;return result;
    }
    private static int rank(int type){return type==3?0:type==2?1:type==0?2:type==4?3:4;}
    private double part(ComposerProject.Part p,int index,double start,ComposerProject.Chord afterPart){
        event(start,3,0,index,p.bars,p.rhythm,0,0,0);
        for(int ch=0;ch<p.tracks.size();ch++){
            TrackConfig t=p.tracks.get(ch);event(start,2,ch,0,0,t.program,t.bank,t.pan,t.muted?0:t.volume);
            if(t.voice){for(VoicePhrase ph:p.phrases)if(ph.trackId.equals(t.id))phrase(ph,t,ch,start,p.bars*bar);continue;}
            // Expression and pitch bend can be left by a recorded performance on this channel.
            event(start,4,ch,0,1,0,0,0,0);
            int[] previous=previousVoicings.get(t.id);
            for(int b=0;b<p.bars;b++){
                List<ComposerProject.Chord> chords=p.chords.get(b);
                for(int c=0;c<chords.size();c++){
                    double pos=start+b*bar+(c==1?MusicTheory.split(project.meter):0);
                    double length=chords.size()==1?bar:c==0?MusicTheory.split(project.meter):bar-MusicTheory.split(project.meter);
                    ComposerProject.Chord next=c+1<chords.size()?chords.get(c+1):b+1<p.bars?p.chords.get(b+1).get(0):afterPart;
                    previous=play(t,ch,chords.get(c),next,pos,length,start+b*bar,p.rhythm,b,p.bars,p,previous);
                }
            }
            if(previous!=null)previousVoicings.put(t.id,previous);
        }return start+p.bars*bar;
    }
    private void phrase(VoicePhrase phrase,TrackConfig t,int channel,double start,double duration){
        for(VoicePhrase.Note n:phrase.notes){
            if(n.beat>=duration)continue;
            int root=n.midi;
            if(n.automatic&&n.degree>=0){int target=MusicTheory.pc(project.key+MusicTheory.SCALE[project.scale][n.degree]);root+=Math.floorMod(target-MusicTheory.pc(root)+6,12)-6;}
            int q=n.automatic&&n.degree>=0?MusicTheory.quality(project.scale,n.degree,false):n.quality;
            int[] tones;
            if(!phrase.chordMode||t.family()==4)tones=new int[]{root};
            else if(t.family()==3)tones=MusicTheory.guitar(root,Math.max(0,Math.min(10,q)),0);
            else{int[] intervals=MusicTheory.INTERVALS[Math.max(0,Math.min(10,q))];tones=new int[intervals.length];for(int i=0;i<tones.length;i++)tones[i]=root+intervals[i];}
            event(start+n.beat,4,channel,(int)n.bend,n.expression,0,0,0,0);
            for(double[] point:n.curve)if(n.beat+point[0]<duration)event(start+n.beat+point[0],4,channel,(int)point[1],point[2],0,0,0,0);
            for(int i=0;i<tones.length;i++){double offset=t.family()==3?i*n.offset:0;if(offset>=n.length)continue;note(start+n.beat+offset,Math.min(n.length-offset,duration-n.beat-offset),channel,tones[i],n.velocity/127.,false);}
        }
    }
    private double ms(double milliseconds){return milliseconds*MusicTheory.quarterBpm(project.bpm,project.meter)/60000.;}
    // The same pulse gets the same displacement on every instrument, in milliseconds.
    private double groove(double at,double humanize){
        long pulse=Math.round(at*24);long x=project.seed ^ (pulse*0x9E3779B97F4A7C15L);
        x^=x>>>30;x*=0xBF58476D1CE4E5B9L;x^=x>>>27;x*=0x94D049BB133111EBL;x^=x>>>31;
        return ms((((x>>>11)*0x1.0p-53)*2-1)*8*humanize);
    }
    private void autoNote(double nominal,double length,int ch,int pitch,double velocity,double start,double end,ComposerProject.Part part,double offset){
        pitch=Math.max(0,Math.min(127,pitch));
        double at=Math.max(start,Math.min(end,nominal+groove(nominal,part.humanize)+offset));
        double off=Math.min(end,at+length);if(off-at<.001)return;
        double[] prior=offEvent[ch][pitch];
        if(prior!=null&&at<=pendingOn[ch][pitch]+1e-9)return;
        if(prior!=null&&pendingOff[ch][pitch]>at){prior[0]=at;pendingOff[ch][pitch]=at;}
        pendingOn[ch][pitch]=at;
        long pulse=Math.round(nominal*24);long hash=project.seed^(pulse*0x9E3779B97F4A7C15L)^((long)ch<<32)^pitch;
        hash^=hash>>>30;hash*=0xBF58476D1CE4E5B9L;hash^=hash>>>27;hash*=0x94D049BB133111EBL;hash^=hash>>>31;
        double variation=1+((((hash>>>11)*0x1.0p-53)*2)-1)*.07*part.humanize;
        double gain=Math.max(.05,Math.min(1,velocity*(.65+.65*part.energy)*variation));
        event(at,1,ch,pitch,gain,0,0,0,0);double[] stop={off,0,ch,pitch,0,0,0,0,0};events.add(stop);offEvent[ch][pitch]=stop;pendingOff[ch][pitch]=off;
    }
    private static double[] subdivisions(double length,double step){double[] result=new double[(int)Math.ceil(length/step)];for(int i=0;i<result.length;i++)result[i]=i*step;return result;}
    private static int pattern(TrackConfig t){int count=t.drums()?5:t.family()>=7&&t.family()<=10?5:7;return t.pattern>=0&&t.pattern<count?t.pattern:0;}
    private int[] play(TrackConfig t,int ch,ComposerProject.Chord chord,ComposerProject.Chord next,double pos,double length,double barStart,int rhythm,int barIndex,int bars,ComposerProject.Part part,int[] previous){
        double end=pos+length;int root=48+chord.root,bass=36+(chord.bass>=0?chord.bass:chord.root);int[] tones=MusicTheory.INTERVALS[chord.quality];int family=t.family();
        if(t.drums()){
            boolean fill=part.fill&&barIndex==bars-1;
            for(ComposerRhythm.Hit hit:ComposerRhythm.drums(rhythm,fill,barIndex,part.energy,pattern(t))){
                double at=barStart+hit.beat;if(at>=pos-.00001&&at<end-.00001)autoNote(at,ms(70),ch,hit.note,hit.gain,pos,end,part,hit.note==38||hit.note==37?ms(5*part.humanize):0);
            }return previous;
        }
                if(family==4){
            int pMod = pattern(t);
            if(pMod == 6){
                autoNote(pos, Math.min(length * .95, 3.8), ch, MusicTheory.fit(bass, 24, 46), .88, pos, end, part, 0);
                return previous;
            }
            double[] attacks = (pMod == 3 || rhythm==6&&(pMod==1||pMod==2)) ? subdivisions(bar, 1) : (pMod == 4 || pMod == 5) ? subdivisions(bar, .5) : ComposerRhythm.kicks(rhythm, barIndex);
            boolean anchored=false;for(double a:attacks)if(Math.abs(barStart+a-pos)<.00001)anchored=true;
            if(!anchored)autoNote(pos,Math.min(.65,length*.75),ch,MusicTheory.fit(bass,28,55),.72,pos,end,part,0);
            for(int i=0;i<attacks.length;i++){
                double at=barStart+attacks[i];if(at<pos-.00001||at>=end-.00001)continue;
                int pitch=bass;
                if(pMod==1&&i%2==1)pitch+=7;
                else if((pMod==2||pMod==4)&&i%2==1)pitch+=12;
                else if(pMod==3&&Math.abs(at-pos)>.00001)pitch+=tones[i%tones.length];
                boolean last=true;for(int j=i+1;j<attacks.length;j++)if(barStart+attacks[j]<end-.00001){last=false;break;}
                if(next!=null&&last&&at>pos+.00001&&end-at<=1.01&&barIndex%4==3&&part.energy>.4){int target=36+(next.bass>=0?next.bass:next.root);pitch=target+(target>=bass?-1:1);}
                double until=i+1<attacks.length?barStart+attacks[i+1]-at:end-at;
                double dur = (pMod == 4) ? Math.min(.22, until*.6) : Math.min(until*.8, end-at);
                autoNote(at,dur,ch,MusicTheory.fit(pitch,28,55),pMod==4?.85:.78,pos,end,part,0);
            }
            return previous;
        }
        if(family==3){
            int pMod = pattern(t);
            int guitarPat = pMod == 3 ? 3 : 0;
            int[] voicing=MusicTheory.guitar(root,chord.quality,guitarPat,previous,chord.bass);
            if(pMod==6){
                // Thumb on the low string with an independent upper-string melody.
                // Restart at each harmony boundary, including short split chords.
                for(double at=0;at<length-.01;at+=.5){
                    int pulse=(int)Math.round(at*2);
                    if(pulse%2==0&&voicing.length>0)autoNote(pos+at,Math.min(.9,length-at),ch,voicing[0],.68,pos,end,part,0);
                    if(voicing.length>1){int n=voicing.length-1-(pulse%Math.min(3,voicing.length-1));
                        autoNote(pos+at,Math.min(.72,length-at),ch,voicing[n],pulse%2==0?.52:.59,pos,end,part,ms(pulse%2==0?12:0));}
                }return voicing;
            }
            if(pMod==5){
                // Sparse, swung upper-string comping rather than full eighth-note strums.
                for(double at=0;at<length-.01;at+=1){double attack=at+(at==0?0:2./3);
                    if(attack>=length)continue;
                    for(int string=1;string<Math.min(4,voicing.length);string++){
                        int n=voicing.length-string;
                        autoNote(pos+attack,Math.min(.28,length-attack),ch,voicing[n],at==0?.58:.46,pos,end,part,ms((string-1)*7));
                    }
                }return voicing;
            }
            boolean isArp = pMod == 1;
            double[] attacks = isArp ? subdivisions(bar, .5) : (pMod == 2) ? subdivisions(bar, .5) : (pMod == 4) ? subdivisions(bar, .25) : (pMod == 5) ? subdivisions(bar, .5) : ComposerRhythm.strums(rhythm);
            for(int i=0;i<attacks.length;i++){
                double at=barStart+attacks[i];if(at<pos-.00001||at>=end-.00001)continue;
                if(isArp){if(voicing.length>0)autoNote(at,Math.min(.65,end-at),ch,voicing[i%voicing.length],.6,pos,end,part,0);}
                else {boolean up=i%2==1;int count=up?Math.min(4,voicing.length):voicing.length;
                    for(int string=0;string<count;string++){
                        int n=up?voicing.length-1-string:string;
                        double offset=ms(string*(up?7:10));
                        double sustain=(pMod==2)? .14 : (pMod==3)? .35 : Math.min(.85,end-at);
                        autoNote(at,sustain,ch,voicing[n],up?.48:.63,pos,end,part,offset);
                    }
                }
            }return voicing;
        }
        if(family==7||family==8||family==9||family==10){
            int pMod = pattern(t);
            double step = (pMod == 1 || pMod == 4) ? length : (pMod == 2 || pMod == 3) ? .5 : 1;
            for(double at=0;at<length-.01;at+=step){
                int n=tones[(int)Math.round(at/step)%tones.length];
                int pitch=MusicTheory.fit(60+chord.root+n,55,pMod==4?72:84);
                double gate=Math.min(pMod==2?.25:step*.8,length-at);
                autoNote(pos+at,gate,ch,pitch,.54,pos,end,part,0);
                if(pMod==4)autoNote(pos+at,gate,ch,pitch+12,.43,pos,end,part,0);
            }return previous;
        }
        int pMod = pattern(t);
        int[] voicing=MusicTheory.voicing(60+chord.root,chord.quality,previous,48,79);
        if(pMod==5||pMod==6){
            for(double at=0;at<length-.01;at+=1){
                // Straight offbeats vs triplet offbeats, with separate gates and accents.
                double attack=at+(pMod==6?2./3:.5);
                if(attack>=length-.001)continue;
                for(int pitch:voicing)autoNote(pos+attack,Math.min(pMod==6?.30:.18,length-attack),ch,pitch,pMod==6?.56:.46,pos,end,part,0);
            }return voicing;
        }
        boolean sustain=pMod==2||(pMod!=1&&pMod!=3&&((t.program>=40&&t.program<=44)||family==6||family==11));
        double step=sustain?length:(pMod==1)? .5 : (pMod==3)? .25 : (pMod==4)? .75 : 1;
        for(double at=0;at<length-.01;at+=step){
            if((pMod==1||pMod==3)&&!sustain)autoNote(pos+at,Math.min(step*.85,length-at),ch,voicing[(int)Math.round(at/step)%voicing.length],.5,pos,end,part,0);
            else for(int pitch:voicing)autoNote(pos+at,Math.min(sustain?length-at:step*.88,length-at),ch,pitch,.5,pos,end,part,0);
        }return voicing;
    }
}
