package pro.livemic.studio;

import org.junit.Test;
import static org.junit.Assert.*;

/** Placement uses the captured callback anchor; file samples themselves stay unshifted. */
public class SessionControllerTest {
    @Test public void calibratedVocalStartsAfterExactCallbackAnchor(){
        SessionProject.Clip clip=new SessionProject.Clip();clip.lengthFrames=48000;
        SessionController.placeRecording(clip,12345,85);
        assertEquals(16425,clip.startFrame);assertEquals(0,clip.sourceOffsetFrames);assertEquals(48000,clip.lengthFrames);
    }
    @Test public void negativePlacementCropsCanonicalFramesWithoutChangingSourceRate(){
        SessionProject.Clip clip=new SessionProject.Clip();clip.lengthFrames=SessionProject.sourceToProject(44100,44100);
        SessionController.placeRecording(clip,960,-100);
        assertEquals(0,clip.startFrame);assertEquals(3840,clip.sourceOffsetFrames);assertEquals(44160,clip.lengthFrames);
        assertEquals(3528,SessionProject.projectToSource(clip.sourceOffsetFrames,44100));
    }
    @Test public void invalidCropLeavesCandidateUntouched(){
        SessionProject.Clip clip=new SessionProject.Clip();clip.startFrame=123;clip.sourceOffsetFrames=12;clip.lengthFrames=480;
        try{SessionController.placeRecording(clip,0,-100);fail();}catch(IllegalStateException expected){}
        assertEquals(123,clip.startFrame);assertEquals(12,clip.sourceOffsetFrames);assertEquals(480,clip.lengthFrames);
    }
}
