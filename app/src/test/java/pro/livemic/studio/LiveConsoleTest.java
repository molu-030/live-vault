package pro.livemic.studio;

import android.os.Bundle;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowLooper;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, instrumentedPackages = {"pro.livemic.studio"}, shadows = {StudioScreenshotTest.Audio.class, StudioScreenshotTest.Composer.class, StudioScreenshotTest.Harmony.class})
@LooperMode(LooperMode.Mode.PAUSED)
public class LiveConsoleTest {
    MainActivity a;

    @Before
    public void setup() throws Exception {
        AppLanguage.set(RuntimeEnvironment.getApplication(), true);
        AudioService.active = false;
        AudioService.playbackActive = false;
        RecordingManager.active = false;
        RecordingManager.paused = false;
        AudioService.composerRequested = false;
        StudioScreenshotTest.Audio.supplied = null;
        StudioScreenshotTest.Audio.changes.clear();
        a = Robolectric.buildActivity(MainActivity.class).setup().get();
        a.handler.removeCallbacks(a.tick);
        a.composer.worker.submit(() -> {}).get();
        ShadowLooper.idleMainLooper();
        a.handler.removeCallbacks(a.tick);
        a.chooseTab(0);
    }

    @After
    public void cleanup() {
        a.handler.removeCallbacks(a.tick);
        a.io.shutdown();
        AudioService.composerRequested = false;
        AudioService.active = false;
        RecordingManager.active = false;
        RecordingManager.paused = false;
        StudioScreenshotTest.Audio.supplied = null;
        AppLanguage.set(RuntimeEnvironment.getApplication(), false);
    }

    static float knobValue(StudioKnob knob) {
        return knob.createAccessibilityNodeInfo().getRangeInfo().getCurrent();
    }

    static void adjust(StudioKnob knob, int percent) {
        Bundle value = new Bundle();
        value.putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, percent / 100f);
        assertTrue(knob.performAccessibilityAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId(), value));
        assertTrue(knob.onKeyDown(KeyEvent.KEYCODE_DPAD_UP,
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP)));
    }

    @Test
    public void mainEffectKnobsPersistAndWriteOnlyReverbAndEcho() {
        StudioKnob[] sliders = {a.liveScreen.reverbVolume, a.liveScreen.echoVolume};
        int[] ids = {2, 4};
        for (int i = 0; i < sliders.length; i++) {
            StudioScreenshotTest.Audio.changes.clear();
            adjust(sliders[i], 35);
            assertFalse(StudioScreenshotTest.Audio.changes.isEmpty());
            for (float[] change : StudioScreenshotTest.Audio.changes) assertEquals(ids[i], (int) change[0]);
            assertEquals(a.values[ids[i]], a.getPreferences(0).getFloat("p" + ids[i], -1), .001);
        }
        float reverb = a.values[2], echo = a.values[4];
        a.chooseTab(4);
        a.chooseTab(0);
        assertEquals(reverb, knobValue(a.liveScreen.reverbVolume), .001);
        assertEquals(echo, knobValue(a.liveScreen.echoVolume), .001);
    }

    static SeekBar dialogSlider(View view) {
        if(view instanceof SeekBar)return (SeekBar)view;
        if(view instanceof ViewGroup) {
            ViewGroup group=(ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++){SeekBar bar=dialogSlider(group.getChildAt(i));if(bar!=null)return bar;}
        }
        return null;
    }
    @Test public void echoPopupUsesActualPercentForChipStepAndAccessibleSliderAndAppliesOnBack() {
        float mic=a.values[0],backing=a.values[1],reverb=a.values[2];
        a.liveScreen.echoVolume.performClick();
        android.app.Dialog dialog=org.robolectric.shadows.ShadowDialog.getLatestDialog();
        assertNotNull(dialog);assertTrue(dialog.isShowing());
        View content=dialog.getWindow().getDecorView();SeekBar slider=dialogSlider(content);assertNotNull(slider);
        assertEquals("Echo slider exposes actual0–80 percent",80,slider.getMax());
        Button chip=StudioScreenshotTest.findButton(content,"40%");assertNotNull(chip);chip.performClick();
        assertEquals(.4f,a.values[4],.001);assertEquals(40,slider.getProgress());
        assertEquals(.4f,knobValue(a.liveScreen.echoVolume),.001);
        StudioScreenshotTest.findButton(content,"+5%").performClick();
        assertEquals(.45f,a.values[4],.001);assertEquals(45,slider.getProgress());
        StudioScreenshotTest.findButton(content,"−5%").performClick();
        assertEquals(.4f,a.values[4],.001);assertEquals(40,slider.getProgress());
        Bundle progress=new Bundle();progress.putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE,65f);
        assertTrue(slider.performAccessibilityAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId(),progress));
        assertEquals(.65f,a.values[4],.001);assertEquals(.65f,knobValue(a.liveScreen.echoVolume),.001);
        assertEquals(.65f,a.getPreferences(0).getFloat("p4",-1),.001);
        a.onBackPressed();assertFalse(dialog.isShowing());
        assertEquals("Popup applies changes immediately, including native Back",.65f,a.values[4],.001);
        a.liveScreen.echoVolume.performClick();dialog=org.robolectric.shadows.ShadowDialog.getLatestDialog();
        content=dialog.getWindow().getDecorView();slider=dialogSlider(content);assertEquals(65,slider.getProgress());
        StudioScreenshotTest.findButton(content,"80%").performClick();
        StudioScreenshotTest.findButton(content,"+5%").performClick();
        assertEquals("Echo remains capped at80 percent",.8f,a.values[4],.001);assertEquals(80,slider.getProgress());
        StudioScreenshotTest.findButton(content,"Done").performClick();assertFalse(dialog.isShowing());
        assertEquals(.8f,a.getPreferences(0).getFloat("p4",-1),.001);
        assertEquals(mic,a.values[0],0);assertEquals(backing,a.values[1],0);assertEquals(reverb,a.values[2],0);
    }

    static void touch(StudioKnob knob, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(0, 16, action, x, y, 0);
        try { knob.onTouchEvent(event); } finally { event.recycle(); }
    }

    void layoutLive() {
        a.root.measure(View.MeasureSpec.makeMeasureSpec(412, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(893, View.MeasureSpec.EXACTLY));
        a.root.layout(0, 0, 412, 893);
    }

    @Test public void mainKnobTapAndHorizontalSwipeNeverChangeValues() {
        layoutLive();
        for (StudioKnob knob : new StudioKnob[]{a.liveScreen.reverbVolume, a.liveScreen.echoVolume}) {
            float initial = knobValue(knob), x = knob.getWidth()/2f, y = knob.getHeight()/2f;
            StudioScreenshotTest.Audio.changes.clear();
            touch(knob, MotionEvent.ACTION_DOWN, x, y);
            touch(knob, MotionEvent.ACTION_UP, x, y);
            touch(knob, MotionEvent.ACTION_DOWN, x, y);
            touch(knob, MotionEvent.ACTION_MOVE, x+60, y+3);
            touch(knob, MotionEvent.ACTION_UP, x+60, y+3);
            assertEquals(initial, knobValue(knob), 0);
            assertTrue("Tap and horizontal drag must not write sound parameters", StudioScreenshotTest.Audio.changes.isEmpty());
        }
    }

    @Test public void mainKnobVerticalDragBothDirectionsWritesOnlyItsParameterAndPersists() {
        layoutLive();
        StudioKnob[] knobs = {a.liveScreen.reverbVolume, a.liveScreen.echoVolume};
        int[] keys = {2,4};
        for (int i=0; i<knobs.length; i++) {
            StudioKnob knob = knobs[i];
            a.values[keys[i]]=.4f; a.liveScreen.sync();
            float x=knob.getWidth()/2f, y=knob.getHeight()/2f;
            StudioScreenshotTest.Audio.changes.clear();
            touch(knob, MotionEvent.ACTION_DOWN, x,y);
            touch(knob, MotionEvent.ACTION_MOVE, x,y-24);
            assertTrue("Up increases amount",a.values[keys[i]]>.4f);
            touch(knob, MotionEvent.ACTION_UP, x,y-24);
            float high=a.values[keys[i]];
            touch(knob, MotionEvent.ACTION_DOWN, x,y);
            touch(knob, MotionEvent.ACTION_MOVE, x,y+24);
            touch(knob, MotionEvent.ACTION_UP, x,y+24);
            assertTrue("Down decreases amount",a.values[keys[i]]<high);
            assertFalse(StudioScreenshotTest.Audio.changes.isEmpty());
            for(float[] change:StudioScreenshotTest.Audio.changes) assertEquals(keys[i],(int)change[0]);
            assertEquals(a.values[keys[i]],a.getPreferences(0).getFloat("p"+keys[i],-1),.001);
        }
    }

    @Test public void compactKnobVerticalDragSurvivesScrollableParentDispatch() {
        ScrollView scroll=new ScrollView(a);
        LinearLayout content=new LinearLayout(a);content.setOrientation(LinearLayout.VERTICAL);
        content.addView(new View(a),new LinearLayout.LayoutParams(360,100));
        List<Float> updates=new ArrayList<>();
        StudioKnob knob=new StudioKnob(a,"Reverb",0,1,.4f,v->Math.round(v*100)+"%",updates::add).compact();
        content.addView(knob,new LinearLayout.LayoutParams(360,96));
        content.addView(new View(a),new LinearLayout.LayoutParams(360,600));
        scroll.addView(content);
        scroll.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(240,View.MeasureSpec.EXACTLY));
        scroll.layout(0,0,360,240);
        assertTrue("Fixture must have a scrollable ancestor",content.getHeight()>scroll.getHeight());
        int[] actions={MotionEvent.ACTION_DOWN,MotionEvent.ACTION_MOVE,MotionEvent.ACTION_UP};
        float[] ys={148,124,124};
        for(int i=0;i<actions.length;i++) {
            MotionEvent event=MotionEvent.obtain(0,16L*i,actions[i],180,ys[i],0);
            assertTrue(scroll.dispatchTouchEvent(event));event.recycle();
        }
        assertFalse("Parent dispatch must deliver knob drag changes",updates.isEmpty());
        assertTrue("Upward drag increases knob amount",knobValue(knob)>.4f);
        assertEquals("Knob drag must not scroll the ancestor",0,scroll.getScrollY());
    }

    @Test public void mainKnobRejectsOutsideDialAndCancellationAllowsPollToSynchronize() {
        layoutLive();
        StudioKnob knob=a.liveScreen.reverbVolume;
        float x=knob.getWidth()/2f,y=knob.getHeight()/2f;
        StudioScreenshotTest.Audio.changes.clear();
        MotionEvent outside=MotionEvent.obtain(0,0,MotionEvent.ACTION_DOWN,0,0,0);
        assertFalse("Outside dial start is rejected",knob.onTouchEvent(outside));outside.recycle();
        touch(knob,MotionEvent.ACTION_MOVE,0,-50);
        touch(knob,MotionEvent.ACTION_UP,0,-50);
        assertTrue(StudioScreenshotTest.Audio.changes.isEmpty());
        touch(knob,MotionEvent.ACTION_DOWN,x,y);
        float initial=knobValue(knob);
        a.values[2]=initial>.2f?.1f:.6f;
        a.liveScreen.poll(new float[22],false);
        assertEquals("Polling must preserve an active touch",initial,knobValue(knob),0);
        touch(knob,MotionEvent.ACTION_CANCEL,x,y);
        a.liveScreen.poll(new float[22],false);
        assertEquals("Cancellation releases synchronization",a.values[2],knobValue(knob),.001);
        assertTrue("Poll and cancellation do not write audio",StudioScreenshotTest.Audio.changes.isEmpty());
    }

    @Test public void mainKnobsExposeKeyboardAndAccessibleRangeWithRealValueDescription() {
        for(StudioKnob knob:new StudioKnob[]{a.liveScreen.reverbVolume,a.liveScreen.echoVolume}) {
            AccessibilityNodeInfo info=knob.createAccessibilityNodeInfo();
            assertEquals(SeekBar.class.getName(),info.getClassName());
            assertNotNull(info.getRangeInfo());
            assertEquals(0,info.getRangeInfo().getMin(),0);
            assertTrue(info.getRangeInfo().getMax()>0);
            Bundle value=new Bundle();value.putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE,.4f);
            assertTrue(knob.performAccessibilityAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId(),value));
            assertEquals(.4f,knobValue(knob),.001);
            assertTrue(knob.getContentDescription().toString().contains("40%"));
            knob.onKeyDown(KeyEvent.KEYCODE_DPAD_UP,new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_UP));
            assertTrue(knobValue(knob)>.4f);
            knob.onKeyDown(KeyEvent.KEYCODE_DPAD_DOWN,new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_DOWN));
            assertEquals(.4f,knobValue(knob),.001);
            assertTrue(knob.performAccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD,null));
            assertTrue(knobValue(knob)<.4f);
            assertTrue(knob.performAccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD,null));
            assertEquals(.4f,knobValue(knob),.001);
        }
    }

    @Test @Config(sdk=35) public void mainKnobsReserveNoSystemGestureExclusionAndRejectScreenEdges() {
        StudioKnob knob=new StudioKnob(a,"Reverb",0,1,.4f,v->Math.round(v*100)+"%",v->fail("Edge gesture changed value")).compact();
        FrameLayout host=new FrameLayout(a);
        host.addView(knob,new FrameLayout.LayoutParams(48,96));
        host.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY));
        host.layout(0,0,360,800);
        MotionEvent edge=MotionEvent.obtain(0,0,MotionEvent.ACTION_DOWN,20,48,0);
        assertFalse("Left screen edge start belongs to system navigation",knob.onTouchEvent(edge));edge.recycle();
        knob.layout(312,0,360,96);
        MotionEvent right=MotionEvent.obtain(0,0,MotionEvent.ACTION_DOWN,340,48,0);
        right.offsetLocation(-312,0);
        assertFalse("Right screen edge start belongs to system navigation",knob.onTouchEvent(right));right.recycle();
        for(View view:new View[]{a.root,a.page,a.liveScreen.reverbVolume,a.liveScreen.echoVolume,knob})
            assertTrue("No system gesture exclusion rectangles",view.getSystemGestureExclusionRects().isEmpty());
    }

    @Test
    public void synchronizationAndPollingNeverWriteAudioParameters() {
        StudioScreenshotTest.Audio.changes.clear();
        a.liveScreen.sync();
        a.liveScreen.poll(new float[22], false);
        assertTrue(StudioScreenshotTest.Audio.changes.isEmpty());
    }

    @Test
    public void resetDoesNotStopMicOrRecordingOrTouchOtherParameters() {
        AudioService.active = true;
        RecordingManager.active = true;
        a.values[11] = 1;
        a.values[13] = 1;
        a.values[0] = 1.4f;
        a.values[1] = .6f;
        StudioScreenshotTest.Audio.changes.clear();
        a.liveScreen.reset();
        assertTrue(AudioService.active);
        assertTrue(RecordingManager.active);
        assertEquals(1, a.values[11], 0);
        assertEquals(1, a.values[13], 0);
        assertEquals(1.4f, a.values[0], 0);
        assertEquals(.6f, a.values[1], 0);
        Set<Integer> keys = new HashSet<>();
        for (float[] v : StudioScreenshotTest.Audio.changes) keys.add((int) v[0]);
        assertTrue(keys.containsAll(Arrays.asList(2, 3, 4, 5, 6)));
        for (int key : new int[]{0, 1, 11, 13}) assertFalse(keys.contains(key));
    }

    @Test
    public void displayShowsIndependentActualLevelsPitchAndRecordingTime() {
        float[] stats = new float[22];
        stats[0] = 440;
        stats[1] = 17.5f;
        stats[2] = 69;
        stats[3] = .1f;
        stats[4] = .01f;
        stats[8] = 12;
        RecordingManager.active = true;
        a.liveScreen.poll(stats, true);
        assertTrue(a.liveScreen.display.note.contains("A4"));
        assertEquals(440, a.liveScreen.display.frequency, .01);
        assertEquals(17.5f, a.liveScreen.display.cents, .001);
        assertEquals(-20, a.liveScreen.display.inputDb, .01);
        assertEquals(-40, a.liveScreen.display.outputDb, .01);
        assertTrue(a.liveScreen.display.recordClock.contains("00:12"));
        stats[0] = 0;
        stats[3] = 0;
        stats[4] = Float.NaN;
        a.liveScreen.poll(stats, true);
        assertFalse(a.liveScreen.display.note.contains("A4"));
        assertEquals(0, a.liveScreen.display.frequency, 0);
        assertEquals(0, a.liveScreen.display.cents, 0);
        assertFalse(Float.isNaN(a.liveScreen.display.inputDb));
        assertFalse(Float.isNaN(a.liveScreen.display.outputDb));
        stats[3] = Float.POSITIVE_INFINITY;
        stats[4] = -.1f;
        a.liveScreen.poll(stats, false);
        assertFalse(Float.isNaN(a.liveScreen.display.inputDb));
        assertFalse(Float.isNaN(a.liveScreen.display.outputDb));
    }

    @Test
    public void idleAndBusyStatesDoNotDisplayStaleRecordingDuration() {
        float[] stats = new float[22];
        stats[8] = 999;
        stats[0] = Float.NaN;
        stats[2] = 69;
        a.liveScreen.poll(stats, false);
        assertEquals("00:00", a.liveScreen.display.recordClock);
        assertEquals("READY", a.liveScreen.display.recordStatus);
        assertEquals("—", a.liveScreen.display.note);
        assertEquals(0, a.liveScreen.display.cents, 0);
        a.busy = true;
        a.liveScreen.poll(stats, false);
        assertEquals("PREPARING / SAVING", a.liveScreen.display.recordStatus);
        assertEquals("00:00", a.liveScreen.display.recordClock);
        a.busy = false;
    }

    @Test
    public void presetAndSafetyMuteKeepRecordingAndMicrophoneActive() {
        AudioService.active = true;
        RecordingManager.active = true;
        float[] stats = new float[22];
        stats[10] = 1;
        stats[17] = 1;
        StudioScreenshotTest.Audio.supplied = stats;
        StudioScreenshotTest.Audio.changes.clear();
        a.monitoringOptions();
        assertTrue(AudioService.active);
        assertTrue(RecordingManager.active);
        for (float[] change : StudioScreenshotTest.Audio.changes) assertNotEquals(11, (int) change[0]);
        a.preset(1);
        assertTrue(AudioService.active);
        assertTrue(RecordingManager.active);
        assertEquals(.3, a.values[2], .001);
        assertTrue(a.liveScreen.reverbVolume.getContentDescription().toString().contains("30%"));
    }

    @Test
    public void videoOpensPreviewAndIsDisabledWhileBusy() {
        a.liveScreen.video.performClick();
        assertEquals(3, a.tab);
        assertFalse(RecordingManager.active);
        a.chooseTab(0);
        a.liveScreen.transport(true, false, false);
        assertFalse(a.liveScreen.video.isEnabled());
        a.liveScreen.transport(false, true, false);
        assertFalse(a.liveScreen.video.isEnabled());
        a.liveScreen.transport(false, false, false);
        assertTrue(a.liveScreen.video.isEnabled());
    }

    @Test
    public void pausedAndMonitorStatusComeFromStats() {
        float[] stats = new float[22];
        stats[8] = 12;
        stats[21] = 1;
        RecordingManager.active = true;
        RecordingManager.paused = true;
        a.liveScreen.poll(stats, true);
        assertTrue(a.liveScreen.display.recordClock.contains("00:12"));
        assertEquals("PAUSED", a.liveScreen.display.recordStatus);
        assertTrue(a.liveScreen.display.routeStatus.toLowerCase(java.util.Locale.US).contains("monitoring on"));
        RecordingManager.paused = false;
    }

    static Switch findSwitch(View v, String label) {
        if (v instanceof Switch && ((Switch) v).getText().toString().contains(label)) return (Switch) v;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                Switch found = findSwitch(g.getChildAt(i), label);
                if (found != null) return found;
            }
        }
        return null;
    }

    @Test
    public void liveBackingVolumePersistsAndWritesOnlyBackingGain() {
        AudioService.active = true;
        boolean composerPlaying = a.composer.playing();
        android.media.AudioManager manager = (android.media.AudioManager)a.getSystemService(android.content.Context.AUDIO_SERVICE);
        int musicVolume = manager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC);
        int voiceVolume = manager.getStreamVolume(android.media.AudioManager.STREAM_VOICE_CALL);
        float micGain = a.values[0];
        a.liveScreen.backingVolume.setProgress(40);
        StudioScreenshotTest.Audio.changes.clear();
        assertTrue(a.liveScreen.backingVolume.onKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT)));
        assertFalse(StudioScreenshotTest.Audio.changes.isEmpty());
        for (float[] change : StudioScreenshotTest.Audio.changes) assertEquals(1, (int) change[0]);
        float value = a.values[1];
        assertTrue(value > 0);
        assertEquals(value, a.getPreferences(0).getFloat("p1", -1), .001);
        assertTrue(AudioService.active);
        assertEquals(composerPlaying, a.composer.playing());
        assertEquals(micGain, a.values[0], 0);
        assertEquals(musicVolume, manager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC));
        assertEquals(voiceVolume, manager.getStreamVolume(android.media.AudioManager.STREAM_VOICE_CALL));
        a.chooseTab(4);
        a.chooseTab(0);
        assertEquals(Math.round(value * 100), a.liveScreen.backingVolume.getProgress());
    }

    @Test
    public void sourceChooserRetainsBackingSourcesAndDeviceAccess() {
        a.liveScreen.inputButton.performClick();
        android.app.Dialog inputDialog = org.robolectric.shadows.ShadowDialog.getLatestDialog();
        assertNotNull(inputDialog);
        assertTrue(inputDialog.isShowing());
        inputDialog.dismiss();

        a.outputButton.performClick();
        android.app.Dialog outputDialog = org.robolectric.shadows.ShadowDialog.getLatestDialog();
        assertNotNull(outputDialog);
        assertTrue(outputDialog.isShowing());
        outputDialog.dismiss();

        a.liveScreen.openSource(1);
        assertEquals(1, a.tab);
        assertEquals(1, a.sourceTab);
        a.nav.getChildAt(1).performClick();
        assertEquals(7, a.tab);
        a.nav.getChildAt(2).performClick();
        assertEquals(4, a.tab);
        a.nav.getChildAt(0).performClick();
        assertEquals(0, a.tab);
        a.liveScreen.openSource(2);
        assertEquals(2, a.sourceTab);
    }

    @Test
    public void monitorStatusUsesStatsEvenWhenCachedSettingDisagrees() {
        float[] stats = new float[22];
        a.values[11] = 1;
        stats[21] = 0;
        a.liveScreen.poll(stats, true);
        assertTrue(a.liveScreen.display.routeStatus.toLowerCase(java.util.Locale.US).contains("monitoring off"));
        a.values[11] = 0;
        stats[21] = 1;
        a.liveScreen.poll(stats, true);
        assertTrue(a.liveScreen.display.routeStatus.toLowerCase(java.util.Locale.US).contains("monitoring on"));
        stats[17] = 1;
        a.liveScreen.poll(stats, true);
        assertTrue(a.liveScreen.display.routeStatus.toLowerCase(java.util.Locale.US).contains("safety mute"));
    }

    @Test
    public void safetyResetKeepsRecordingAndEffectGainsWhileRestoringMonitor() {
        AudioService.active = true;
        RecordingManager.active = true;
        RecordingManager.micOnly = true;
        float[] stats = new float[22];
        stats[10] = 1;
        stats[17] = 1;
        StudioScreenshotTest.Audio.supplied = stats;
        float[] before = a.values.clone();
        StudioScreenshotTest.Audio.changes.clear();
        StudioScreenshotTest.Composer.monitorCalls.clear();
        int previousResets = StudioScreenshotTest.Audio.feedbackResets;
        a.liveScreen.toggleMonitor();
        assertEquals(previousResets + 1, StudioScreenshotTest.Audio.feedbackResets);
        assertTrue(RecordingManager.active);
        assertTrue(RecordingManager.micOnly);
        assertTrue(AudioService.active);
        assertFalse(StudioScreenshotTest.Composer.monitorCalls.isEmpty());
        for (boolean enabled : StudioScreenshotTest.Composer.monitorCalls) assertTrue(enabled);
        assertEquals(1, StudioScreenshotTest.Audio.changes.size());
        assertEquals(11, StudioScreenshotTest.Audio.changes.get(0)[0], 0);
        assertEquals(1, StudioScreenshotTest.Audio.changes.get(0)[1], 0);
        for (int key = 0; key < before.length; key++) {
            if (key != 11) assertEquals("effect parameter " + key, before[key], a.values[key], 0);
        }
        RecordingManager.micOnly = false;
    }

    @Test
    public void noiseReductionInDetailedEffectsUpdatesParameter() {
        a.chooseTab(6);
        Switch noise = findSwitch(a.page, "Noise reduction");
        assertNotNull(noise);
        boolean next = !noise.isChecked();
        StudioScreenshotTest.Audio.changes.clear();
        noise.performClick();
        assertEquals(next ? 1 : 0, a.values[19], 0);
        assertEquals(a.values[19], a.getPreferences(0).getFloat("p19", -1), 0);
        assertEquals(19, (int) StudioScreenshotTest.Audio.changes.get(0)[0]);
        a.chooseTab(0);
        assertEquals(next ? 1 : 0, a.values[19], 0);
    }

    static int countSliders(View view) {
        int count = view.createAccessibilityNodeInfo().getRangeInfo() != null ? 1 : 0;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup)view;
            for (int i = 0; i < group.getChildCount(); i++) count += countSliders(group.getChildAt(i));
        }
        return count;
    }

    @Test
    public void detailsKeepAdvancedEffectsAndPresetResetActions() {
        assertEquals("Only mic, backing, reverb and echo gains on main", 4, countSliders(a.page));
        a.liveScreen.detailsButton.performClick();
        android.app.Dialog dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog();
        assertNotNull(dialog);
        View content = dialog.getWindow().getDecorView();
        assertEquals("Details keep decay, delay, feedback and three EQ bands", 6, countSliders(content));
        assertNull("Reverb amount remains on main", StudioScreenshotTest.findDescription(content, "Reverb amount"));
        assertNull("Echo amount remains on main", StudioScreenshotTest.findDescription(content, "Echo amount"));
        for (String name : new String[]{"Echo delay", "Echo feedback", "Low EQ", "Mid EQ", "High EQ"})
            assertNotNull("Advanced effect retained: " + name, StudioScreenshotTest.findDescription(content, name));
        View decay = StudioScreenshotTest.findDescription(content, "Reverb decay");
        assertNotNull(decay);
        Bundle value = new Bundle();
        value.putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, 3.4f);
        StudioScreenshotTest.Audio.changes.clear();
        assertTrue(decay.performAccessibilityAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId(), value));
        assertEquals(3.4f, a.values[3], .001);
        assertEquals(3.4f, a.getPreferences(0).getFloat("p3", -1), .001);
        assertFalse(StudioScreenshotTest.Audio.changes.isEmpty());
        for (float[] change : StudioScreenshotTest.Audio.changes) {
            assertEquals(3, (int) change[0]);
            assertEquals(3.4f, change[1], .001);
        }
        assertNotNull(StudioScreenshotTest.findButtonContaining(content, "Preset"));
        assertNotNull(StudioScreenshotTest.findButtonContaining(content, "Reset"));
        dialog.dismiss();
    }

    @Test
    public void playingSelectedFileDoesNotRequestMicrophonePermission() {
        org.robolectric.Shadows.shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(android.Manifest.permission.RECORD_AUDIO);
        a.sourceTab = 1;
        a.selectedFile = "local-backing.wav";
        a.awaitingMicPermission = false;
        StudioScreenshotTest.Audio.localPlayCalls.clear();
        StudioScreenshotTest.Audio.microphoneStarts = 0;
        a.liveScreen.backingPlayButton.performClick();
        assertFalse(a.awaitingMicPermission);
        assertFalse(AudioService.active);
        assertEquals(0, StudioScreenshotTest.Audio.microphoneStarts);
        assertTrue(StudioScreenshotTest.Audio.localPlayCalls.contains(true));
    }

    @Test
    public void pausedCurrentProjectResumesComposerInsteadOfStaleFile() throws Exception {
        a.selectedFile = "stale-file.wav";
        AudioService.composerRequested = true;
        StudioScreenshotTest.Audio.localPlayCalls.clear();
        StudioScreenshotTest.Composer.transportCalls.clear();
        a.liveScreen.backingPlayButton.performClick();
        a.composer.worker.submit(() -> {}).get();
        ShadowLooper.idleMainLooper();
        assertTrue(StudioScreenshotTest.Composer.transportCalls.contains(1));
        assertFalse(StudioScreenshotTest.Audio.localPlayCalls.contains(true));
        assertTrue(AudioService.composerRequested);
    }

    @Test
    public void captureLocksDeviceSettings() {
        a.composer.capturing = true;
        assertTrue(a.settingsLocked());
        a.composer.capturing = false;
        a.harmony.state[4] = 0;
        assertTrue(a.settingsLocked());
        a.harmony.state[4] = -1;
        a.harmony.state[5] = 0;
        assertTrue(a.settingsLocked());
        a.harmony.state[5] = -1;
        assertFalse(a.settingsLocked());
    }

    @Test
    public void monitorOffAndOnUpdatesComposerWithoutStoppingRecordingOrBacking() {
        AudioService.active = true;
        AudioService.playbackActive = true;
        RecordingManager.active = true;
        StudioScreenshotTest.Composer.monitorCalls.clear();
        StudioScreenshotTest.Composer.transportCalls.clear();
        StudioScreenshotTest.Audio.changes.clear();
        a.set(11, 0);
        a.set(11, 1);
        assertEquals(Arrays.asList(false, true), StudioScreenshotTest.Composer.monitorCalls);
        assertTrue(AudioService.active);
        assertTrue(AudioService.playbackActive);
        assertTrue(RecordingManager.active);
        assertTrue(StudioScreenshotTest.Composer.transportCalls.isEmpty());
        assertEquals(2, StudioScreenshotTest.Audio.changes.size());
        for (float[] change : StudioScreenshotTest.Audio.changes) assertEquals(11, (int) change[0]);
    }

    @Test
    public void hamburgerMenuNavigatesToLiveComposerAndLibrary() {
        assertEquals(View.GONE, a.nav.getVisibility());
        a.drawer();
        View liveItem = StudioScreenshotTest.findDescription(a.drawerPopup.getContentView(), "Live,");
        assertNotNull(liveItem);
        liveItem.performClick();
        assertEquals(0, a.tab);

        a.drawer();
        View composerItem = StudioScreenshotTest.findDescription(a.drawerPopup.getContentView(), "Composer,");
        assertNotNull(composerItem);
        composerItem.performClick();
        assertEquals(7, a.tab);

        a.drawer();
        View libraryItem = StudioScreenshotTest.findDescription(a.drawerPopup.getContentView(), "Library,");
        assertNotNull(libraryItem);
        libraryItem.performClick();
        assertEquals(4, a.tab);
    }

    @Test
    public void liveMicVolumeAndMonitorToggleSyncWithParameters() {
        a.chooseTab(0);
        StudioScreenshotTest.Audio.changes.clear();
        a.liveScreen.micVolume.setProgress(150);
        assertTrue(a.liveScreen.micVolume.onKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT)));
        assertFalse(StudioScreenshotTest.Audio.changes.isEmpty());
        assertEquals(0, (int) StudioScreenshotTest.Audio.changes.get(0)[0]);
        assertTrue(a.values[0] > 1.4f);
        assertTrue(a.liveScreen.micVolumeLabel.getText().toString().contains(Math.round(a.values[0] * 100) + "%"));
        assertEquals(a.values[0], a.getPreferences(0).getFloat("p0", -1), .001);

        useHeadphones();
        AudioService.active = true;
        float prevMonitor = a.values[11];
        float[] stats = new float[22];
        stats[21] = prevMonitor;
        StudioScreenshotTest.Audio.supplied = stats;
        a.liveScreen.monitorToggle.performClick();
        assertNotEquals(prevMonitor, a.values[11], .001f);
        stats[21] = a.values[11]; // Native set updates the engine state before the next click.
        a.liveScreen.monitorToggle.performClick();
        assertEquals(prevMonitor, a.values[11], .001f);
    }

    void useHeadphones() {
        android.media.AudioManager manager = (android.media.AudioManager)a.getSystemService(android.content.Context.AUDIO_SERVICE);
        android.media.AudioDeviceInfo headphones = org.robolectric.shadows.AudioDeviceInfoBuilder.newBuilder()
                .setType(android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES).build();
        org.robolectric.Shadows.shadowOf(manager).setOutputDevices(Collections.singletonList(headphones));
    }

    @Test
    public void activeMonitorClicksFollowActualStateWhenCachedSettingDisagrees() {
        useHeadphones();
        AudioService.active = true;
        AudioService.playbackActive = true;
        RecordingManager.active = true;
        float[] stats = new float[22];
        stats[16] = 1;
        StudioScreenshotTest.Audio.supplied = stats;
        for (boolean settingsDialog : new boolean[]{false, true}) {
            android.app.Dialog dialog = null;
            View control = a.liveScreen.monitorToggle;
            if (settingsDialog) {
                a.monitoringOptions();
                dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog();
                control = StudioScreenshotTest.findButtonContaining(dialog.getWindow().getDecorView(), "Voice monitoring");
                assertNotNull(control);
            }
            for (int actual : new int[]{0, 1}) {
                a.values[11] = 1 - actual;
                stats[21] = actual;
                StudioScreenshotTest.Audio.changes.clear();
                control.performClick();
                assertEquals(1 - actual, a.values[11], 0);
                assertFalse(StudioScreenshotTest.Audio.changes.isEmpty());
                for (float[] change : StudioScreenshotTest.Audio.changes) {
                    assertEquals(11, (int)change[0]);
                    assertEquals(1 - actual, change[1], 0);
                }
                assertTrue(AudioService.active);
                assertTrue(AudioService.playbackActive);
                assertTrue(RecordingManager.active);
                assertEquals(1, stats[16], 0);
            }
            if (dialog != null) dialog.dismiss();
        }
    }

    @Test
    public void audioSettingsExposesAllFourProcessingSwitches() {
        a.chooseTab(6);
        Switch monitor = findSwitch(a.page, "Voice monitoring");
        Switch noise = findSwitch(a.page, "Noise reduction");
        Switch echo = findSwitch(a.page, "Echo cancellation");
        Switch agc = findSwitch(a.page, "Auto gain control");
        assertNotNull(monitor);
        assertNotNull(noise);
        assertNotNull(echo);
        assertNotNull(agc);

        boolean nextEcho = !echo.isChecked();
        echo.performClick();
        assertEquals(nextEcho, AudioRouting.echoCancellationPreferred(a));

        boolean nextAgc = !agc.isChecked();
        agc.performClick();
        assertEquals(nextAgc, AudioRouting.autoGainPreferred(a));
    }

    @Test
    public void topBarLogoAndBrandReturnHomeAndEffectsShareCompactRow() {
        a.root.measure(View.MeasureSpec.makeMeasureSpec(412, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(893, View.MeasureSpec.EXACTLY));
        a.root.layout(0, 0, 412, 893);
        // Main effects share a compact row with separate touch targets.
        android.graphics.Rect rev = StudioScreenshotTest.bounds(a, a.liveScreen.reverbVolume);
        android.graphics.Rect echo = StudioScreenshotTest.bounds(a, a.liveScreen.echoVolume);
        assertEquals("Reverb and echo share a row", rev.top, echo.top);
        assertTrue("Reverb and echo do not overlap", rev.right <= echo.left);

        // 2. Logo and brand click from another tab returns to tab 0
        LinearLayout header = (LinearLayout) a.root.getChildAt(0);
        View logo = header.getChildAt(1);
        View brand = header.getChildAt(2);

        a.chooseTab(7);
        assertEquals(7, a.tab);
        logo.performClick();
        assertEquals(0, a.tab);

        a.chooseTab(7);
        assertEquals(7, a.tab);
        brand.performClick();
        assertEquals(0, a.tab);

        // 3. Drawer exposes Backing through its clickable row, not a Button.
        a.drawer();
        assertNotNull(a.drawerPopup);
        View drawer = a.drawerPopup.getContentView();
        assertNotNull(StudioScreenshotTest.findDescription(drawer, "Backing,"));
        assertNull(StudioScreenshotTest.findButtonContaining(drawer, "Mute output"));
        assertNull(StudioScreenshotTest.findButtonContaining(drawer, "출력 끄기"));
        a.drawerPopup.dismiss();
    }
}
