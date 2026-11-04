package pro.livemic.studio;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class ComposerPatternTest {
    private static ComposerProject project(int meter,int program,int pattern) {
        ComposerProject p=new ComposerProject();p.meter=meter;p.bpm=45;
        ComposerProject.Part part=p.parts.get(0);part.rhythm=meter==0?0:meter==1?6:7;part.humanize=0;part.fill=false;
        TrackConfig t=part.tracks.get(0);t.program=program;t.pattern=pattern;
        return p;
    }
    private static ComposerTransport.Plan plan(ComposerProject p){return new ComposerTransport().compile(p,-1);}
    private static List<double[]> firstBar(ComposerProject p){
        ComposerTransport.Plan plan=plan(p);List<double[]> notes=new ArrayList<>();
        for(int i=0;i<plan.events.length;i+=9)if(plan.events[i+1]==1&&plan.events[i+2]==0&&plan.events[i]<plan.bar)notes.add(Arrays.copyOfRange(plan.events,i,i+9));
        return notes;
    }
    private static int count(List<ComposerRhythm.Hit> hits,int note){int n=0;for(ComposerRhythm.Hit h:hits)if(h.note==note)n++;return n;}
    @Test public void kitPatternsChangeActualGmNotesAndDensityWithoutChangingStandard(){
        for(int rhythm:new int[]{0,1,2,3,4,5,6,7,8}){
            List<ComposerRhythm.Hit> base=ComposerRhythm.drums(rhythm,false,0,.6),standard=ComposerRhythm.drums(rhythm,false,0,.6,0);
            assertEquals(base.size(),standard.size());
            for(int i=0;i<base.size();i++){assertEquals(base.get(i).beat,standard.get(i).beat,0);assertEquals(base.get(i).note,standard.get(i).note);assertEquals(base.get(i).gain,standard.get(i).gain,0);}
            List<ComposerRhythm.Hit> light=ComposerRhythm.drums(rhythm,false,0,.6,1),drive=ComposerRhythm.drums(rhythm,false,0,.6,2),ride=ComposerRhythm.drums(rhythm,false,0,.6,3),rim=ComposerRhythm.drums(rhythm,false,0,.6,4);
            assertTrue(light.size()<base.size());assertTrue(rim.size()<base.size());assertTrue(count(drive,36)>count(base,36));
            assertTrue(count(ride,51)>0);assertEquals(0,count(ride,42)+count(ride,46)+count(ride,49));
            assertTrue(count(rim,37)>0);assertEquals(0,count(rim,38));assertEquals(1,count(rim,36));
        }
    }
    @Test public void keyboardCompingHasStraightAndSwungOffbeatsWithShortGates(){
        for(int meter=0;meter<3;meter++){
            ComposerProject straight=project(meter,0,5),swing=project(meter,0,6);
            List<double[]> a=firstBar(straight),b=firstBar(swing);
            assertFalse(a.isEmpty());assertEquals(.5,a.get(0)[0],1e-9);assertEquals(2./3,b.get(0)[0],1e-9);
            assertTrue(firstBar(project(meter,0,0)).get(0)[0]<a.get(0)[0]);
            ComposerTransport.Plan plan=plan(straight);
            for(int i=0;i<plan.events.length;i+=9)if(plan.events[i+1]==0&&plan.events[i+2]==0&&plan.events[i]<1)assertEquals(.68,plan.events[i],1e-9);
        }
    }
    @Test public void fingerstyleCombinesThumbAndMelodyAndJazzUsesSwungUpperChords(){
        for(int meter=0;meter<3;meter++){
            ComposerProject p=project(meter,24,6);ComposerProject.Part part=p.parts.get(0);
            for(List<ComposerProject.Chord> bar:part.chords){bar.get(0).automatic=false;bar.get(0).root=0;bar.get(0).quality=0;bar.get(0).bass=4;}
            List<double[]> notes=firstBar(p);assertEquals(0,notes.get(0)[0],0);assertEquals(4,((int)notes.get(0)[3])%12);
            int simultaneous=0;for(double[] n:notes){assertTrue(n[3]>=40&&n[3]<=76);if(n[0]<.02)simultaneous++;}
            assertTrue("thumb plus upper melody",simultaneous>=2);
            List<double[]> arp=firstBar(project(meter,24,1));assertTrue(notes.size()>arp.size());
            List<double[]> jazz=firstBar(project(meter,24,5));boolean swung=false;for(double[] n:jazz)if(Math.abs(n[0]-5./3)<.001)swung=true;assertTrue(swung);
            assertTrue(jazz.size()<firstBar(project(meter,24,0)).size());
        }
    }
    @Test public void octaveUnisonIsAnActualSimultaneousOctavePair(){
        for(int program:new int[]{56,64,72,80}){
            List<double[]> notes=firstBar(project(0,program,4));assertEquals(2,notes.size());
            assertEquals(notes.get(0)[0],notes.get(1)[0],0);assertEquals(12,notes.get(1)[3]-notes.get(0)[3],0);
        }
    }
    @Test public void bassOptionsHaveDistinctRolesInEveryMeter(){
        for(int meter=0;meter<3;meter++){
            Set<String> roles=new HashSet<>();
            for(int pattern=0;pattern<7;pattern++){
                List<double[]> notes=firstBar(project(meter,33,pattern));StringBuilder role=new StringBuilder();
                ComposerTransport.Plan plan=plan(project(meter,33,pattern));
                for(int i=0;i<plan.events.length;i+=9)if(plan.events[i+2]==0&&plan.events[i]<plan.bar&&(plan.events[i+1]==0||plan.events[i+1]==1))role.append(plan.events[i]).append(':').append(plan.events[i+1]).append(':').append(plan.events[i+3]).append(';');
                assertTrue("duplicate bass role in meter "+meter+" pattern "+pattern,roles.add(role.toString()));
                if(pattern==6){assertEquals(1,notes.size());assertTrue(notes.get(0)[3]<=46);}
            }
        }
    }
    @Test public void kitArticulationRetainsTheClosingFill(){
        for(int rhythm:new int[]{0,6,7})for(int pattern=0;pattern<5;pattern++){
            double length=rhythm==0?4:3;List<ComposerRhythm.Hit> fill=ComposerRhythm.drums(rhythm,true,3,.8,pattern);int tail=0;
            for(ComposerRhythm.Hit h:fill)if(h.beat>=length-1){tail++;assertTrue(h.note==38||h.note==50||h.note==47||h.note==45);assertTrue(h.beat<length);}
            assertEquals(4,tail);
        }
    }
    @Test public void allPatternsStayBalancedInsideSplitHarmonyAndRepeatDeterministically(){
        for(int meter=0;meter<3;meter++)for(int program:new int[]{0,24,33,64})for(int pattern=0;pattern<=7;pattern++){
            ComposerProject p=project(meter,program,pattern);p.parts.get(0).humanize=1;p.parts.get(0).tracks.get(2).pattern=pattern;
            ComposerProject.Chord second=new ComposerProject.Chord();second.degree=4;second.resolve(p);p.parts.get(0).chords.get(0).add(second);
            ComposerTransport.Plan a=plan(p);assertArrayEquals(a.events,plan(p).events,0);
            if(meter==2)assertEquals(67.5,a.bpm,0);
            double[][] active=new double[8][128];for(double[] row:active)Arrays.fill(row,-1);
            for(int i=0;i<a.events.length;i+=9){double at=a.events[i];int type=(int)a.events[i+1],ch=(int)a.events[i+2],note=(int)a.events[i+3];assertTrue(at>=0&&at<=a.beats);
                if(type==1){assertEquals(-1,active[ch][note],0);active[ch][note]=at;}
                if(type==0){double on=active[ch][note];assertTrue(on>=0);assertFalse(on<MusicTheory.split(meter)-1e-9&&at>MusicTheory.split(meter)+1e-9);assertEquals(Math.floor((on+1e-9)/a.bar),Math.floor((at-1e-9)/a.bar),0);active[ch][note]=-1;}
            }
            for(double[] row:active)for(double on:row)assertEquals(-1,on,0);
        }
    }
    @Test public void unsupportedPatternFallsBackToDefault(){
        for(int program:new int[]{0,24,33,64}){ComposerProject p=project(0,program,0);double[] expected=plan(p).events;p.parts.get(0).tracks.get(0).pattern=7;assertArrayEquals(expected,plan(p).events,0);}
    }
    @Test public void exportExplainsExtendedPatternsAndKeepsPhraseSourceOptional() throws Exception {
        ComposerProject p=project(0,24,6);p.parts.get(0).tracks.get(2).pattern=3;
        String description=ComposerLlmExport.arrangement(p);
        assertTrue(description.contains("Ballad fingerstyle"));assertTrue(description.contains("Ride cymbal groove"));
        org.json.JSONObject schema=ComposerLlmExport.schema(),track=schema.getJSONObject("$defs").getJSONObject("track");
        assertTrue(schema.getString("description").contains("6 Swing comping"));
        assertTrue(track.getJSONObject("properties").has("phraseSource"));
        org.json.JSONArray required=track.getJSONArray("required");for(int i=0;i<required.length();i++)assertNotEquals("phraseSource",required.getString(i));
    }
}
