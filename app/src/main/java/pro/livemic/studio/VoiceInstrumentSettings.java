package pro.livemic.studio;
import org.json.*;
final class VoiceInstrumentSettings {
    int program=24,mode=0,octave=0,response=0,quality=-1,quantize=0;
    boolean autoRange=false,snap=false,rhythm=false,mixVoice=false;
    float confidence=.70f,gate=.003f;
    JSONObject json()throws JSONException{return new JSONObject().put("program",program).put("mode",mode).put("octave",octave).put("response",response).put("quality",quality).put("quantize",quantize).put("autoRange",autoRange).put("snap",snap).put("rhythm",rhythm).put("mixVoice",mixVoice).put("confidence",confidence).put("gate",gate);}
    static VoiceInstrumentSettings read(JSONObject j){VoiceInstrumentSettings s=new VoiceInstrumentSettings();if(j==null)return s;s.program=Math.max(0,Math.min(127,j.optInt("program",24)));s.mode=j.optInt("mode")==1?1:0;s.octave=Math.max(-3,Math.min(3,j.optInt("octave")));s.response=j.optInt("response")==1?1:0;s.quality=Math.max(-1,Math.min(10,j.optInt("quality",-1)));s.quantize=j.optInt("quantize");s.autoRange=j.optBoolean("autoRange",s.program>=32&&s.program<40);s.snap=j.optBoolean("snap");s.rhythm=j.optBoolean("rhythm");s.mixVoice=j.optBoolean("mixVoice");s.confidence=(float)Math.max(.5,Math.min(.99,j.optDouble("confidence",.70)));s.gate=(float)Math.max(.001,Math.min(.1,j.optDouble("gate",.003)));return s;}
}
