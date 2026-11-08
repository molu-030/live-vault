package pro.livemic.studio;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class ComposerPhraseRegressionTest {
    private static VoicePhrase.Note note(double beat,double length) {
        VoicePhrase.Note n=new VoicePhrase.Note();n.beat=beat;n.length=length;n.midi=60;n.velocity=100;return n;
    }

    @Test public void expressionStopsBeforeNoteEndpointAndPartEndWithoutChangingSavedCurves()throws Exception {
        ComposerProject p=new ComposerProject();ComposerProject.Part part=p.parts.get(0);
        TrackConfig track=part.tracks.get(0);track.voice=true;track.program=0;
        part.tracks.subList(1,part.tracks.size()).clear();
        VoicePhrase phrase=new VoicePhrase();phrase.trackId=track.id;
        VoicePhrase.Note first=note(0,.72);
        first.curve.add(new double[]{.25,100,.8});
        first.curve.add(new double[]{.5,1100,.2});
        first.curve.add(new double[]{.65,1200,.1});
        phrase.notes.add(first);phrase.notes.add(note(.5,1));
        double end=part.bars*MusicTheory.barBeats(p.meter);
        VoicePhrase.Note last=note(end-.5,2);
        last.curve.add(new double[]{.25,200,.9});
        last.curve.add(new double[]{.5,1300,.1});
        last.curve.add(new double[]{.75,1400,.1});
        phrase.notes.add(last);phrase.quantize(8);part.phrases.add(phrase);
        assertEquals(.5,first.length,0);
        String before=p.json().toString();ComposerTransport.Plan plan=new ComposerTransport().compile(p,-1);
        List<Double> times=new ArrayList<>(),bends=new ArrayList<>(),expressions=new ArrayList<>();
        for(int i=0;i<plan.events.length;i+=ComposerTransport.STRIDE)if(plan.events[i+1]==4&&plan.events[i+2]==0){
            times.add(plan.events[i]);bends.add(plan.events[i+3]);expressions.add(plan.events[i+4]);
        }
        assertEquals(java.util.Arrays.asList(0.,.25,.5,end-.5,end-.25),times);
        assertEquals(java.util.Arrays.asList(0.,100.,0.,0.,200.),bends);
        assertEquals(java.util.Arrays.asList(1.,.8,1.,1.,.9),expressions);
        assertEquals(before,p.json().toString());
    }
}
