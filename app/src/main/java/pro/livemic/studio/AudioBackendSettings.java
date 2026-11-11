package pro.livemic.studio;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONException;
import org.json.JSONObject;

/** Opt-in provider selection; the native setter also rejects every live stream/recording. */
final class AudioBackendSettings {
    static final String KEY="audioBackend";
    interface Port { boolean request(int provider); int[] info(); }
    private final SharedPreferences preferences;
    private final Port port;
    AudioBackendSettings(Context context) {
        this(AudioRouting.prefs(context),new Port(){
            public boolean request(int provider){return NativeAudio.requestBackend(provider);}
            public int[] info(){return NativeAudio.backendInfo();}
        });
    }
    AudioBackendSettings(SharedPreferences preferences,Port port){this.preferences=preferences;this.port=port;}
    int savedChoice(){int value=preferences.getInt(KEY,0);return value==1?1:0;}
    boolean choose(int provider,boolean idle){
        if(!idle || (provider!=0&&provider!=1))return false;
        try {if(!port.request(provider))return false;}catch(RuntimeException|LinkageError unavailable){return false;}
        preferences.edit().putInt(KEY,provider).apply();return true;
    }
    boolean restoreIfStopped(boolean idle){
        // AAudio is already the native default. Never open a stream to apply a preference.
        if(!idle || savedChoice()==0)return false;
        try{return port.request(1);}catch(RuntimeException|LinkageError unavailable){return false;}
    }
    Info info(){try{return new Info(port.info());}catch(RuntimeException|LinkageError unavailable){return new Info(null);}}
    static final class Info {
        final int requested,applied,platform,fallback;
        final boolean available;
        Info(int[] raw){
            available=raw!=null&&raw.length==4&&provider(raw[0])>=0
                    &&raw[1]>=-1&&raw[1]<=1&&raw[2]>=-1&&raw[2]<=1&&raw[3]>=0&&raw[3]<=3;
            if(!available){requested=applied=platform=fallback=-1;return;}
            requested=raw[0];applied=raw[1];platform=raw[2];fallback=raw[3];
        }
        private static int provider(int value){return value==0||value==1?value:-1;}
        private static String providerName(int value){return value==0?"AAudio":value==1?"Oboe":null;}
        private String platformName(){return platform==0?"AAudio":platform==1?"OpenSL ES":null;}
        private String reason(){return fallback==0?"none":fallback==1?"oboe_output_open_or_validation_failed":fallback==2?"oboe_input_open_or_validation_failed":fallback==3?"oboe_start_failed":"unavailable";}
        JSONObject json(){try{return new JSONObject().put("status",available?"available":"unavailable").put("requested",requested<0?JSONObject.NULL:providerName(requested)).put("applied",applied<0?JSONObject.NULL:providerName(applied)).put("platform",platform<0?JSONObject.NULL:platformName()).put("last_start_fallback_reason",reason()).put("hardware_comparison","not_measured");}catch(JSONException e){throw new IllegalStateException("Could not serialize backend status",e);}}
        String text(boolean en){
            String unknown=en?"Unavailable":"확인 불가",idle=en?"Not running":"실행 안 함";
            String r=fallback==0?(en?"None":"없음"):fallback==1?(en?"Oboe output could not open or validate":"Oboe 출력 열기·확인 실패"):fallback==2?(en?"Oboe input could not open or validate":"Oboe 입력 열기·확인 실패"):fallback==3?(en?"Oboe could not start":"Oboe 시작 실패"):unknown;
            return (en?"Requested backend: ":"요청 백엔드: ")+(requested<0?unknown:providerName(requested))
                    +"\n"+(en?"Applied backend: ":"실제 백엔드: ")+(applied<0?(available?idle:unknown):providerName(applied))
                    +"\n"+(en?"Platform audio API: ":"플랫폼 오디오 API: ")+(platform<0?unknown:platformName())
                    +"\n"+(en?"Last start fallback: ":"마지막 시작 대체 경로: ")+r;
        }
    }
}
