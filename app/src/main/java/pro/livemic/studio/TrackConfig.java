package pro.livemic.studio;
import org.json.*;

final class TrackConfig {
    String id=java.util.UUID.randomUUID().toString(),name="악기";
    int program=24,bank=0,pattern=0;float volume=.7f,pan=0;boolean muted=false,voice=false;
    TrackConfig(){}
    TrackConfig(String name,int program,boolean muted){this.name=name;this.program=program;this.muted=muted;}
    boolean drums(){return bank==128;}
    int family(){return drums()?16:program/8;}
    JSONObject json()throws JSONException{return new JSONObject().put("id",id).put("name",name).put("program",program).put("bank",bank).put("pattern",pattern).put("volume",volume).put("pan",pan).put("muted",muted).put("voice",voice);}
    static TrackConfig read(JSONObject j){TrackConfig t=new TrackConfig();t.id=j.optString("id",t.id);t.name=j.optString("name","악기");t.program=Math.max(0,Math.min(127,j.optInt("program",24)));t.bank=j.optInt("bank")==128?128:0;t.pattern=Math.max(0,Math.min(7,j.optInt("pattern")));t.volume=(float)Math.max(0,Math.min(1,j.optDouble("volume",.7)));t.pan=(float)Math.max(-1,Math.min(1,j.optDouble("pan")));t.muted=j.optBoolean("muted");t.voice=j.optBoolean("voice");return t;}
}
