package pro.livemic.studio;
import java.util.*;
/** Host scheduler checks, using real project/transport classes and no Android runtime. */
public final class ArrangementTransitionTest {
    private static void check(boolean yes,String message){if(!yes)throw new AssertionError(message);}
    private static ComposerProject project(int rhythm,float energy){
        ComposerProject p=new ComposerProject();p.meter=rhythm==6?1:rhythm==7?2:0;
        ComposerProject.Part part=p.parts.get(0);part.rhythm=rhythm;part.bars=4;part.energy=energy;part.humanize=0;
        part.chords.clear();for(int root:new int[]{0,5,7,0}){ComposerProject.Chord c=new ComposerProject.Chord();c.automatic=false;c.root=root;c.quality=0;part.chords.add(new ArrayList<>(Collections.singletonList(c)));}
        part.tracks.get(1).pattern=3;return p;
    }
    private static ComposerTransport.Plan compile(ComposerProject p){return new ComposerTransport().compile(p,-1);}
    private static void balance(ComposerTransport.Plan p){
        boolean[][] on=new boolean[8][128];
        for(int i=0;i<p.events.length;i+=9){double at=p.events[i];int type=(int)p.events[i+1],ch=(int)p.events[i+2],note=(int)p.events[i+3];check(at>=0&&at<=p.beats+1e-8,"event outside song");
            if(type==1){check(!on[ch][note],"overlap");on[ch][note]=true;if(ch==1)check(note>=28&&note<=55,"bass range");}
            if(type==0){check(on[ch][note],"unmatched off");on[ch][note]=false;}
        }for(boolean[] row:on)for(boolean active:row)check(!active,"hanging note");
    }
    private static int count(ComposerTransport.Plan p,int ch,double start,double end,boolean cymbal){
        int count=0;for(int i=0;i<p.events.length;i+=9)if(p.events[i+1]==1&&p.events[i+2]==ch&&p.events[i]>=start-1e-9&&p.events[i]<end-1e-9){int n=(int)p.events[i+3];if(!cymbal||n==42||n==44||n==46||n==49||n==51)count++;}return count;
    }
    private static void energyAndApproach(int rhythm){
        ComposerProject low=project(rhythm,.2f),high=project(rhythm,.9f);
        ComposerTransport.Plan a=compile(low),b=compile(high);balance(a);balance(b);
        check(Arrays.equals(b.events,compile(high).events),"same seed must reproduce");
        check(count(a,2,a.beats-1,a.beats,false)==2,"quiet closing fill must have two hits");
        check(count(b,2,b.beats-1,b.beats,false)==4,"energetic closing fill must have four hits");
        check(count(a,2,0,a.bar,true)<=count(b,2,0,b.bar,true),"quiet part must not add cymbals");
        if(rhythm==0)check(count(a,2,0,a.bar,true)<count(b,2,0,b.bar,true),"quiet pop part must leave cymbal space");
        for(int bar=0;bar<4;bar++){
            double start=bar*b.bar,end=start+b.bar;int anchor=-1,last=-1;double lastAt=-1;
            for(int i=0;i<b.events.length;i+=9)if(b.events[i+1]==1&&b.events[i+2]==1&&b.events[i]>=start&&b.events[i]<end){if(anchor<0)anchor=(int)b.events[i+3];last=(int)b.events[i+3];lastAt=b.events[i];}
            int root=high.parts.get(0).chords.get(bar).get(0).root,next=high.parts.get(0).chords.get((bar+1)%4).get(0).root;
            check(anchor%12==root,"walking bass must retain root anchor");
            if(root!=next&&end-lastAt<=1.01){int delta=Math.floorMod(last-next,12);check(delta==1||delta==11,"walking last note must approach next root by semitone");}
            check(high.parts.get(0).chords.get(bar).get(0).root==new int[]{0,5,7,0}[bar]&&!high.parts.get(0).chords.get(bar).get(0).automatic,"manual harmony unchanged");
        }
        high.parts.get(0).fill=false;ComposerTransport.Plan without=compile(high);balance(without);
        for(int i=0;i<without.events.length;i+=9)if(without.events[i+1]==1&&without.events[i+2]==2)check(without.events[i+3]!=50&&without.events[i+3]!=47&&without.events[i+3]!=45,"fill off must not add tom pickups");
    }
    private static void quietMeterPulse(){
        for(int rhythm:new int[]{6,7}){
            ComposerTransport.Plan p=compile(project(rhythm,.2f));Set<Double> starts=new TreeSet<>();
            for(int i=0;i<p.events.length;i+=9)if(p.events[i+1]==1&&p.events[i+2]==2&&p.events[i]<p.bar){int n=(int)p.events[i+3];if(n==42||n==44||n==46||n==49||n==51)starts.add(p.events[i]);}
            Set<Double> expected=new TreeSet<>(rhythm==7?Arrays.asList(0.,1.5):Arrays.asList(0.,1.,2.));
            check(starts.equals(expected),"quiet cymbals must preserve meter pulse: rhythm="+rhythm+" actual="+starts);
        }
    }
    private static void partContrast(){
        ComposerProject p=project(0,.2f),other=project(0,.9f);
        ComposerProject.Part b=other.parts.get(0);p.parts.add(b);p.order.add(new ComposerProject.Order(b.id,1));
        ComposerTransport.Plan plan=compile(p);balance(plan);
        check(plan.beats==32,"A/B order duration");
        check(count(plan,2,15,16,false)==2&&count(plan,2,31,32,false)==4,"A/B closing density follows each part energy");
        check(Arrays.equals(plan.events,compile(p).events),"A/B seed reproducibility");
    }
    private static void recordedTiming(){
        ComposerProject p=project(0,.2f);TrackConfig voice=new TrackConfig("Voice",24,false);voice.voice=true;p.parts.get(0).tracks.add(voice);
        VoicePhrase phrase=new VoicePhrase();phrase.trackId=voice.id;VoicePhrase.Note n=new VoicePhrase.Note();n.midi=64;n.beat=.333;n.length=.6;n.velocity=80;n.curve.add(new double[]{.2,17,.8});phrase.notes.add(n);p.parts.get(0).phrases.add(phrase);
        double[] first=voiceEvents(compile(p),4);p.parts.get(0).energy=.9f;p.parts.get(0).humanize=1;double[] second=voiceEvents(compile(p),4);
        check(Arrays.equals(first,second),"energy/groove must not change recorded events");
        check(n.beat==.333&&n.length==.6&&n.curve.get(0)[0]==.2,"phrase data unchanged");
    }
    private static double[] voiceEvents(ComposerTransport.Plan p,int ch){List<Double> selected=new ArrayList<>();for(int i=0;i<p.events.length;i+=9)if(p.events[i+2]==ch)for(int j=0;j<9;j++)selected.add(p.events[i+j]);double[] out=new double[selected.size()];for(int i=0;i<out.length;i++)out[i]=selected.get(i);return out;}
    public static void main(String[] args){for(int rhythm=0;rhythm<9;rhythm++)energyAndApproach(rhythm);quietMeterPulse();partContrast();recordedTiming();System.out.println("PASS: nine grooves,4/4+3/4+6/8,energy-shaped fill/cymbal density,6/8 dotted-quarter and3/4 quarter pulses,walking approaches,root anchors,ranges,balanced notes,seed reproducibility,recorded timing unchanged");}
}
