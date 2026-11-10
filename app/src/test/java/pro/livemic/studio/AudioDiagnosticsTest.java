package pro.livemic.studio;

import java.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class AudioDiagnosticsTest {
    static double[] fixture(){return new double[]{1,2,3,4,5,6,7,-12.5,2,9,1200,32768,12,13,14,15,160,384,48000,384,48000};}
    static AudioDiagnostics model(double[] values){return new AudioDiagnostics(()->values,()->2000);}
    @Test public void samplingIsThrottledEvenWhenManualOffsetChanges()throws Exception {
        long[] now={0};int[] calls={0};AudioDiagnostics d=new AudioDiagnostics(()->{calls[0]++;return fixture();},()->now[0]);
        AudioDiagnostics.Snapshot first=d.sample();
        for(int i=0;i<100;i++){now[0]=i*9;d.sample().json(i);d.sample().text(true,i);}
        assertEquals(1,calls[0]);now[0]=999;assertSame(first,d.sample());now[0]=1000;assertNotSame(first,d.sample());assertEquals(2,calls[0]);now[0]=1;d.sample();assertEquals(3,calls[0]);
    }
    @Test public void realCounterMappingSeparatesBufferDurationCorrectionAndManualOffset()throws Exception {
        JSONObject r=model(fixture()).sample().json(-75),m=r.getJSONObject("metrics");
        assertEquals(2,m.getInt("output_xruns"));assertEquals(3,m.getInt("input_xruns"));assertEquals(4,m.getInt("input_short_read_blocks"));assertEquals(5,m.getInt("input_read_request_shortfall_frames"));assertEquals(6,m.getInt("input_fifo_underflow_frames"));assertEquals(7,m.getInt("input_fifo_overflow_frames"));
        assertEquals(-12.5,m.getDouble("fifo_correction_ppm_estimate"),0);assertEquals("tracking",m.getString("fifo_correction_state"));assertEquals(9,m.getInt("file_cache_underrun_blocks"));assertEquals(1200,m.getInt("file_cache_ready_frames"));assertEquals(32768,m.getInt("file_cache_capacity_frames"));assertEquals(160,m.getInt("file_cache_missing_frames"));assertEquals(12,m.getInt("source_read_errors"));assertEquals(14,m.getInt("writer_dropped_frames"));assertEquals(15,m.getInt("writer_io_errors"));
        assertEquals(8,r.getDouble("output_buffer_duration_ms"),0);assertEquals(8,r.getDouble("input_fifo_target_duration_ms"),0);assertEquals(-75,r.getInt("manual_recording_offset_ms"));assertTrue(r.isNull("round_trip_latency_ms"));assertEquals("not_measured",r.getString("round_trip_latency_status"));assertEquals("pending",r.getString("usb_hardware_validation"));assertEquals("fifo_occupancy_servo_not_hardware_clock_measurement",r.getString("fifo_correction_basis"));
    }
    @Test public void unknownSchemaMalformedAndUnavailableValuesNeverBecomeZero()throws Exception {
        double[] other=fixture();other[0]=2;
        for(double[] values:new double[][]{null,new double[]{1,2},other}){JSONObject r=model(values).sample().json(0);assertFalse(r.getBoolean("engine_metrics_available"));assertTrue(r.isNull("output_buffer_duration_ms"));assertTrue(r.getJSONObject("metrics").isNull("writer_io_errors"));}
        double[] values=fixture();values[1]=Double.NaN;values[3]=-1;values[7]=Double.POSITIVE_INFINITY;values[20]=0;
        JSONObject r=model(values).sample().json(0),m=r.getJSONObject("metrics");assertTrue(m.isNull("output_xruns"));assertTrue(m.isNull("input_short_read_blocks"));assertTrue(m.isNull("fifo_correction_ppm_estimate"));assertTrue(r.isNull("output_buffer_duration_ms"));
        AudioDiagnostics failed=new AudioDiagnostics(()->{throw new IllegalStateException("/private/device-address");},()->0);assertFalse(failed.sample().json(0).toString().contains("private"));
    }
    @Test public void negativeOneIsAValidSignedCorrectionOnlyWhileEstimating()throws Exception {
        double[] values=fixture();values[7]=-1;
        assertEquals(-1,model(values).sample().json(0).getJSONObject("metrics").getDouble("fifo_correction_ppm_estimate"),0);
        values[8]=0;assertTrue(model(values).sample().json(0).getJSONObject("metrics").isNull("fifo_correction_ppm_estimate"));values[8]=1;assertEquals("warming_up",model(values).sample().fifoState());values[8]=99;assertEquals("unavailable",model(values).sample().fifoState());
    }
    @Test public void snapshotCopiesNativeArrayAndExportsOnlyWhitelistedMetadata()throws Exception {
        double[] values=fixture();AudioDiagnostics.Snapshot snapshot=model(values).sample();values[14]=999;values[19]=999;
        JSONObject r=snapshot.json(5);assertEquals(14,r.getJSONObject("metrics").getInt("writer_dropped_frames"));assertEquals(384,r.getInt("output_buffer_frames"));
        Set<String> expected=new HashSet<>(Arrays.asList("schema","sampled_elapsed_ms","engine_metrics_available","metrics","output_buffer_frames","output_buffer_duration_ms","input_fifo_target_duration_ms","manual_recording_offset_ms","round_trip_latency_ms","round_trip_latency_status","fifo_correction_basis","usb_hardware_validation","counter_scope","sample_interval_ms"));
        Set<String> actual=new HashSet<>();for(Iterator<String> it=r.keys();it.hasNext();)actual.add(it.next());assertEquals(expected,actual);
        for(String forbidden:new String[]{"/data/","content://","device_address","device_name","source_path","other_apps","audio_samples"})assertFalse(forbidden,r.toString().contains(forbidden));assertTrue(snapshot.text(true,5).contains("not measured"));assertTrue(snapshot.text(false,5).contains("미측정"));
    }
}
