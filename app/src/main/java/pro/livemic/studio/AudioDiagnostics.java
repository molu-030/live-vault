package pro.livemic.studio;

import android.os.SystemClock;
import java.util.Locale;
import org.json.JSONException;
import org.json.JSONObject;

/** Read-only engine snapshots. Sampling never changes streams, routing, permissions or the mix. */
final class AudioDiagnostics {
    static final long INTERVAL_MS = 1000;
    static final int SCHEMA = 1, FIELD_COUNT = 21;
    interface Source { double[] read(); }
    interface Clock { long elapsedMillis(); }
    private final Source source;
    private final Clock clock;
    private Snapshot cached;
    AudioDiagnostics() { this(NativeAudio::diagnostics, SystemClock::elapsedRealtime); }
    AudioDiagnostics(Source source, Clock clock) { this.source=source;this.clock=clock; }
    synchronized Snapshot sample() {
        long now=clock.elapsedMillis();
        if(cached==null || now<cached.elapsedMillis || now-cached.elapsedMillis>=INTERVAL_MS) {
            double[] values=null;
            try { values=source.read(); } catch(RuntimeException | LinkageError unavailable) { /* No guessed replacement values. */ }
            cached=new Snapshot(now,values);
        }
        return cached;
    }
    static final class Snapshot {
        final long elapsedMillis;
        final boolean available;
        private final double[] values;
        Snapshot(long time,double[] raw) {
            elapsedMillis=time;values=raw==null?new double[0]:raw.clone();
            available=values.length>=FIELD_COUNT && values[0]==SCHEMA;
        }
        private Double count(int index) {
            return available && Double.isFinite(values[index]) && values[index]>=0 ? values[index] : null;
        }
        String fifoState() {
            Double state=count(8);
            return state==null?"unavailable":state==0?"inactive":state==1?"warming_up":state==2?"tracking":"unavailable";
        }
        private Double correction() {
            String state=fifoState();
            return (state.equals("warming_up")||state.equals("tracking")) && Double.isFinite(values[7]) ? values[7] : null;
        }
        private Double duration(int framesIndex,int rateIndex) {
            Double frames=count(framesIndex),rate=count(rateIndex);
            if(frames==null||rate==null||rate<=0)return null;
            double millis=1000d*frames/rate;
            return Double.isFinite(millis)?millis:null;
        }
        private static Object value(Object n) { return n==null?JSONObject.NULL:n; }
        JSONObject json(int manualOffsetMs) {
            try {
                JSONObject metrics=new JSONObject();
                String[] names={"output_xruns","input_xruns","input_short_read_blocks","input_read_request_shortfall_frames","input_fifo_underflow_frames","input_fifo_overflow_frames"};
                for(int i=0;i<names.length;i++)metrics.put(names[i],value(count(i+1)));
                metrics.put("fifo_correction_ppm_estimate",value(correction())).put("fifo_correction_state",fifoState());
                metrics.put("file_cache_underrun_blocks",value(count(9))).put("file_cache_ready_frames",value(count(10))).put("file_cache_capacity_frames",value(count(11)));
                metrics.put("source_read_errors",value(count(12))).put("source_seek_generation",value(count(13)));
                metrics.put("writer_dropped_frames",value(count(14))).put("writer_io_errors",value(count(15)));
                metrics.put("file_cache_missing_frames",value(count(16))).put("input_fifo_target_frames",value(count(17))).put("fifo_target_frame_sample_rate_hz",value(count(18)));
                return new JSONObject().put("schema",SCHEMA).put("sampled_elapsed_ms",elapsedMillis).put("engine_metrics_available",available).put("metrics",metrics)
                        .put("output_buffer_frames",value(count(19))).put("output_buffer_duration_ms",value(duration(19,20)))
                        .put("input_fifo_target_duration_ms",value(duration(17,18))).put("manual_recording_offset_ms",manualOffsetMs)
                        .put("round_trip_latency_ms",JSONObject.NULL).put("round_trip_latency_status","not_measured")
                        .put("fifo_correction_basis","fifo_occupancy_servo_not_hardware_clock_measurement")
                        .put("usb_hardware_validation","pending").put("counter_scope","engine_stream_and_source_sessions_may_reset_on_restart").put("sample_interval_ms",INTERVAL_MS);
            } catch(JSONException impossible) { throw new IllegalStateException("Could not serialize diagnostics",impossible); }
        }
        private static String n(Double n) { return n==null?"—":String.format(Locale.US,"%.0f",n); }
        private static String ms(Double n) { return n==null?"—":String.format(Locale.US,"%.1f ms",n); }
        private static String tr(boolean en,String ko,String english) { return en?english:ko; }
        String text(boolean en,int offset) {
            String state=fifoState();
            state=state.equals("tracking")?tr(en,"추적 중","tracking"):state.equals("warming_up")?tr(en,"수렴 중","warming up"):state.equals("inactive")?tr(en,"비활성","inactive"):tr(en,"확인 불가","unavailable");
            Double ppm=correction();
            return tr(en,"출력 / 입력 xrun: ","Output / input xrun: ")+n(count(1))+" / "+n(count(2))
                    +"\n"+tr(en,"입력 짧은 읽기 / 요청 미충족 프레임: ","Input short reads / request shortfall frames: ")+n(count(3))+" / "+n(count(4))
                    +"\n"+tr(en,"입력 FIFO 부족 / 초과 프레임: ","Input FIFO underflow / overflow frames: ")+n(count(5))+" / "+n(count(6))
                    +"\n"+tr(en,"FIFO 보정량 (추정): ","FIFO correction (estimate): ")+(ppm==null?"—":String.format(Locale.US,"%+.1f ppm",ppm))+" · "+state
                    +"\n"+tr(en,"입력 FIFO 목표 버퍼: ","Input FIFO target buffer: ")+n(count(17))+tr(en," 프레임 · "," frames · ")+ms(duration(17,18))
                    +"\n"+tr(en,"파일 캐시 부족 블록 / 누락 프레임: ","File cache underrun blocks / missing frames: ")+n(count(9))+" / "+n(count(16))
                    +"\n"+tr(en,"파일 캐시 준비 / 용량 (프레임): ","File cache ready / capacity (frames): ")+n(count(10))+" / "+n(count(11))
                    +"\n"+tr(en,"파일 읽기 오류: ","File read errors: ")+n(count(12))
                    +"\n"+tr(en,"저장 누락 프레임 / 쓰기 오류: ","Writer dropped frames / I/O errors: ")+n(count(14))+" / "+n(count(15))
                    +"\n\n"+tr(en,"출력 버퍼 길이: ","Output buffer duration: ")+ms(duration(19,20))
                    +"\n"+tr(en,"녹음 수동 오프셋: ","Manual recording offset: ")+offset+" ms"
                    +"\n"+tr(en,"실제 왕복 지연: 미측정","Actual round-trip latency: not measured");
        }
    }
}
