package pro.livemic.studio;

import android.content.SharedPreferences;
import java.util.*;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class AudioBackendSettingsTest {
    static class Port implements AudioBackendSettings.Port {
        final List<Integer> requests=new ArrayList<>();boolean accept=true;int[] snapshot={0,-1,-1,0};
        public boolean request(int provider){requests.add(provider);return accept;}public int[] info(){return snapshot;}
    }
    SharedPreferences prefs;Port port;AudioBackendSettings settings;
    @Before public void setup(){prefs=AudioRouting.prefs(RuntimeEnvironment.getApplication());prefs.edit().clear().commit();port=new Port();settings=new AudioBackendSettings(prefs,port);}
    @Test public void defaultDoesNotOptInOrOpenAStream(){assertEquals(0,settings.savedChoice());assertFalse(settings.restoreIfStopped(true));assertTrue(port.requests.isEmpty());}
    @Test public void onlyAcceptedStoppedChoicesPersistAndRestore(){
        assertFalse(settings.choose(1,false));assertFalse(settings.choose(99,true));assertTrue(port.requests.isEmpty());assertTrue(settings.choose(1,true));assertEquals(1,settings.savedChoice());
        AudioBackendSettings reopened=new AudioBackendSettings(prefs,port);assertFalse(reopened.restoreIfStopped(false));assertEquals(1,port.requests.size());assertTrue(reopened.restoreIfStopped(true));assertEquals(Arrays.asList(1,1),port.requests);
        port.accept=false;assertFalse(settings.choose(0,true));assertEquals(1,settings.savedChoice());port.accept=true;assertTrue(settings.choose(0,true));assertEquals(0,settings.savedChoice());
    }
    @Test public void appliedProviderPlatformAndFallbackAreReportedSeparately()throws Exception {
        port.snapshot=new int[]{1,0,0,2};JSONObject r=settings.info().json();assertEquals("Oboe",r.getString("requested"));assertEquals("AAudio",r.getString("applied"));assertEquals("AAudio",r.getString("platform"));assertEquals("oboe_input_open_or_validation_failed",r.getString("last_start_fallback_reason"));assertEquals("not_measured",r.getString("hardware_comparison"));
        port.snapshot=new int[]{1,1,0,0};assertEquals("AAudio",settings.info().json().getString("platform"));port.snapshot=new int[]{1,1,1,0};assertEquals("OpenSL ES",settings.info().json().getString("platform"));
    }
    @Test public void unavailableInfoNeverClaimsAnInactiveOrMeasuredBackend()throws Exception {
        for(int[] invalid:new int[][]{null,new int[]{0},new int[]{1,99,0,0}}){port.snapshot=invalid;AudioBackendSettings.Info info=settings.info();assertFalse(info.available);assertTrue(info.json().isNull("applied"));assertFalse(info.text(true).contains("Not running"));}
        port.snapshot=new int[]{0,-1,-1,0};assertTrue(settings.info().available);assertTrue(settings.info().text(true).contains("Not running"));
    }
}
