package pro.livemic.studio;

import android.app.*;
import android.content.*;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.shadows.*;
import static org.junit.Assert.*;

/** Native Views and SAF fixtures only; no physical USB, drift, audio or latency validation. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,instrumentedPackages={"pro.livemic.studio"},shadows={AudioDiagnosticsUiTest.Audio.class,StudioScreenshotTest.Composer.class,StudioScreenshotTest.Harmony.class,YouTubePlayerTest.OfflineWeb.class})
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class AudioDiagnosticsUiTest {
    @Implements(value=NativeAudio.class,isInAndroidSdk=false)
    public static class Audio extends StudioScreenshotTest.Audio {
        static double[] counters;static int calls,latencyCalls,requested;static boolean requestAllowed;
        static final java.util.List<Integer> requests=new java.util.ArrayList<>();
        @Implementation protected static boolean requestBackend(int provider){requests.add(provider);if(!requestAllowed)return false;requested=provider;return true;}
        @Implementation protected static int[] backendInfo(){return new int[]{requested,-1,-1,0};}
        @Implementation protected static double[] diagnostics(){calls++;return counters.clone();}
        @Implementation protected static int[] latencyInfo(){latencyCalls++;return new int[]{192,1536,384,48000,2};}
    }
    @Before public void reset()throws Exception {new StudioScreenshotTest().resetComposer();ExitCoordinator.resetForLaunch();Audio.counters=AudioDiagnosticsTest.fixture();Audio.calls=Audio.latencyCalls=Audio.requested=0;Audio.requestAllowed=true;Audio.requests.clear();AudioRouting.prefs(RuntimeEnvironment.getApplication()).edit().remove(AudioBackendSettings.KEY).commit();RecordingManager.active=false;}
    @After public void restore(){Dialog d=ShadowDialog.getLatestDialog();if(d!=null)d.dismiss();RuntimeEnvironment.setFontScale(1);AppLanguage.set(RuntimeEnvironment.getApplication(),false);AudioService.active=AudioService.playbackActive=false;RecordingManager.active=false;}
    void settle(MainActivity a)throws Exception {a.handler.removeCallbacks(a.tick);a.store.awaitRecovery();a.io.submit(()->{}).get(5,TimeUnit.SECONDS);a.composer.worker.submit(()->{}).get(5,TimeUnit.SECONDS);ShadowLooper.idleMainLooper();a.handler.removeCallbacks(a.tick);}
    @Test public void largeTextDetailsPreserveMixAndRecordingAndKeepActionsReachable()throws Exception {
        WorkflowScreenshotTest renderer=new WorkflowScreenshotTest();renderer.directory=new File(System.getProperty("user.dir"),"build/studio-screenshots/diagnostics");assertTrue(renderer.directory.isDirectory()||renderer.directory.mkdirs());
        for(int[] size:new int[][]{{412,893},{1480,924},{960,600}})for(boolean en:new boolean[]{false,true}){
            MainActivity a=TabletLayoutTest.open(size[0],size[1],1.3f,en);
            try {
                a.chooseTab(6);settle(a);Button details=StudioScreenshotTest.findButtonContaining(a.page,en?"Audio diagnostics":"오디오 진단");assertNotNull(details);
                float[] mix=a.values.clone();int starts=StudioScreenshotTest.Audio.microphoneStarts,stops=StudioScreenshotTest.Audio.stopCalls,latency=Audio.latencyCalls;StudioScreenshotTest.Audio.changes.clear();AudioService.active=true;RecordingManager.active=true;details.performClick();
                Dialog d=ShadowDialog.getLatestDialog();String prefix=size[0]+"x"+size[1]+"-"+(en?"en":"ko")+"-130-SYNTHETIC";
                View decor=renderer.sheet(a,d,size[0],size[1],prefix+"-top");WorkflowScreenshotTest.textFits(decor);TextView readout=decor.findViewWithTag("audio-diagnostics-readout");assertNotNull(readout);assertTrue(readout.getText().toString().contains(en?"not measured":"미측정"));assertTrue(readout.getText().toString().contains("-12.5 ppm"));
                Button save=WorkflowScreenshotTest.button(decor,en?"Save JSON":"JSON 저장"),cancel=WorkflowScreenshotTest.button(decor,en?"Cancel":"취소");WorkflowScreenshotTest.touch(a,save);WorkflowScreenshotTest.touch(a,cancel);android.graphics.Rect fixed=WorkflowScreenshotTest.rect(decor,save);
                ScrollView body=WorkflowScreenshotTest.scroll(decor);assertNotNull(body);body.scrollTo(0,100000);renderer.sheet(a,d,size[0],size[1],prefix+"-bottom");assertEquals(fixed,WorkflowScreenshotTest.rect(decor,save));assertTrue(WorkflowScreenshotTest.rect(decor,decor).contains(WorkflowScreenshotTest.rect(decor,cancel)));
                cancel.performClick();ShadowLooper.idleMainLooper();assertFalse(d.isShowing());assertArrayEquals(mix,a.values,0);assertTrue(StudioScreenshotTest.Audio.changes.isEmpty());assertEquals(starts,StudioScreenshotTest.Audio.microphoneStarts);assertEquals(stops,StudioScreenshotTest.Audio.stopCalls);assertEquals("No second live-stream JNI getter",latency,Audio.latencyCalls);assertTrue(AudioService.active);assertTrue(RecordingManager.active);
            } finally {AudioService.active=false;RecordingManager.active=false;a.handler.removeCallbacksAndMessages(null);TabletLayoutTest.close(a);}
        }
    }
    @Test public void backendChoiceIsOptInAndNeverStopsActiveAudio()throws Exception {
        ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup();MainActivity a=controller.get();settle(a);
        try {
            assertTrue(Audio.requests.isEmpty());assertTrue(a.applyAudioBackendChoice(1));assertEquals(1,AudioRouting.prefs(a).getInt(AudioBackendSettings.KEY,-1));
            int requests=Audio.requests.size(),stops=StudioScreenshotTest.Audio.stopCalls,starts=StudioScreenshotTest.Audio.microphoneStarts;StudioScreenshotTest.Audio.changes.clear();
            AudioService.active=true;assertFalse(a.applyAudioBackendChoice(0));AudioService.active=false;
            AudioService.playbackActive=true;assertFalse(a.applyAudioBackendChoice(0));AudioService.playbackActive=false;
            RecordingManager.active=true;assertFalse(a.applyAudioBackendChoice(0));assertTrue(RecordingManager.active);RecordingManager.active=false;
            a.starting=true;assertFalse(a.applyAudioBackendChoice(0));a.starting=false;a.pendingAction=1;assertFalse(a.applyAudioBackendChoice(0));a.pendingAction=0;
            assertEquals(requests,Audio.requests.size());assertEquals(stops,StudioScreenshotTest.Audio.stopCalls);assertEquals(starts,StudioScreenshotTest.Audio.microphoneStarts);assertTrue(StudioScreenshotTest.Audio.changes.isEmpty());
            Audio.requestAllowed=false;assertFalse(a.applyAudioBackendChoice(0));assertEquals(1,AudioRouting.prefs(a).getInt(AudioBackendSettings.KEY,-1));
            Audio.requestAllowed=true;assertTrue(a.applyAudioBackendChoice(0));assertEquals(0,AudioRouting.prefs(a).getInt(AudioBackendSettings.KEY,-1));
        } finally {AudioService.active=AudioService.playbackActive=false;RecordingManager.active=false;a.pendingAction=0;a.starting=false;a.handler.removeCallbacksAndMessages(null);controller.pause().stop().destroy();}
    }
    public static class Provider extends ContentProvider {
        static File destination;static boolean reject;
        public boolean onCreate(){return true;}public String getType(Uri u){return "application/json";}public Cursor query(Uri u,String[] p,String s,String[] a,String order){return null;}public Uri insert(Uri u,ContentValues v){return null;}public int delete(Uri u,String s,String[] a){return 0;}public int update(Uri u,ContentValues v,String s,String[] a){return 0;}
        @Override public ParcelFileDescriptor openFile(Uri u,String mode)throws FileNotFoundException {if(reject)throw new FileNotFoundException("/private/provider/device-address-secret");return ParcelFileDescriptor.open(destination,ParcelFileDescriptor.MODE_CREATE|ParcelFileDescriptor.MODE_TRUNCATE|ParcelFileDescriptor.MODE_WRITE_ONLY);}
    }
    void provider(MainActivity a){Provider p=new Provider();ProviderInfo info=new ProviderInfo();info.authority="diagnostics.fixture";p.attachInfo(a,info);ShadowContentResolver.registerProviderInternal(info.authority,p);}
    @Test public void safRecreationExportsTheSelectedSnapshotWithoutPrivateMetadata()throws Exception {
        ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup();MainActivity a=controller.get();settle(a);
        try {
            a.selectedFile="/private/secret-take.wav";a.exportAudioDiagnostics();Intent picker=Shadows.shadowOf(a).getNextStartedActivityForResult().intent;assertEquals(Intent.ACTION_CREATE_DOCUMENT,picker.getAction());assertEquals("application/json",picker.getType());assertTrue(picker.hasCategory(Intent.CATEGORY_OPENABLE));assertEquals("live-vault-audio-diagnostics.json",picker.getStringExtra(Intent.EXTRA_TITLE));
            Bundle state=new Bundle();controller.saveInstanceState(state).pause().stop().destroy();Audio.counters[14]=999;controller=Robolectric.buildActivity(MainActivity.class).create(state).start().resume().visible();a=controller.get();settle(a);
            Provider.reject=false;Provider.destination=new File(a.getCacheDir(),"diagnostics-export.json");provider(a);a.onActivityResult(MainActivity.EXPORT_AUDIO_DIAGNOSTICS,Activity.RESULT_OK,new Intent().setData(Uri.parse("content://diagnostics.fixture/chosen-output")));a.io.submit(()->{}).get(5,TimeUnit.SECONDS);ShadowLooper.idleMainLooper();
            String text=new String(Files.readAllBytes(Provider.destination.toPath()),StandardCharsets.UTF_8);JSONObject r=new JSONObject(text);assertEquals(14,r.getJSONObject("metrics").getInt("writer_dropped_frames"));assertTrue(r.isNull("round_trip_latency_ms"));assertEquals("pending",r.getString("usb_hardware_validation"));assertFalse(text.contains("private"));assertFalse(text.contains("secret-take"));assertFalse(text.contains("chosen-output"));
        } finally {a.handler.removeCallbacksAndMessages(null);controller.pause().stop().destroy();}
    }
    @Test public void canceledOrFailedSafNeverFallsBackToPrivateStorageOrLeaksProviderPaths()throws Exception {
        ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup();MainActivity a=controller.get();settle(a);AppLanguage.set(a,true);
        try {
            Provider.destination=new File(a.getCacheDir(),"not-created-diagnostics.json");Provider.destination.delete();Provider.reject=false;provider(a);a.exportAudioDiagnostics();Shadows.shadowOf(a).getNextStartedActivityForResult();a.onActivityResult(MainActivity.EXPORT_AUDIO_DIAGNOSTICS,Activity.RESULT_CANCELED,null);a.onActivityResult(MainActivity.EXPORT_AUDIO_DIAGNOSTICS,Activity.RESULT_OK,new Intent().setData(Uri.parse("content://diagnostics.fixture/stale")));a.io.submit(()->{}).get(5,TimeUnit.SECONDS);assertFalse(Provider.destination.exists());
            a.exportAudioDiagnostics();Shadows.shadowOf(a).getNextStartedActivityForResult();Provider.reject=true;a.onActivityResult(MainActivity.EXPORT_AUDIO_DIAGNOSTICS,Activity.RESULT_OK,new Intent().setData(Uri.parse("content://diagnostics.fixture/private-destination")));a.io.submit(()->{}).get(5,TimeUnit.SECONDS);ShadowLooper.idleMainLooper();assertEquals("Could not save diagnostics JSON.",String.valueOf(ShadowToast.getTextOfLatestToast()));assertFalse(Provider.destination.exists());
        } finally {Provider.reject=false;a.handler.removeCallbacksAndMessages(null);controller.pause().stop().destroy();}
    }
}
