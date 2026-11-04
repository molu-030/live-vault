package pro.livemic.studio;

import org.json.*;
import java.io.*;
import java.util.*;

/** Strictly validate AI-produced v1 JSON before the backward-compatible reader can normalize it. */
final class ComposerLlmImport {
    static final int MAX_BYTES=8*1024*1024;
    static final class Invalid extends Exception {Invalid(String path){super(path);}}
    private static void fail(String p)throws Invalid{throw new Invalid(p);}
    private static void keys(JSONObject o,String path,String... allowed)throws Invalid{Set<String> names=new HashSet<>(Arrays.asList(allowed));Iterator<String> it=o.keys();while(it.hasNext()){String key=it.next();if(!names.contains(key))fail(path+"."+key+" unknown");}}
    private static void seed(JSONObject o)throws Exception{Object value=o.get("seed");if(!(value instanceof Number))fail("project.seed");try{new java.math.BigDecimal(value.toString()).longValueExact();}catch(ArithmeticException|NumberFormatException e){fail("project.seed");}}
    private static JSONObject obj(JSONObject o,String k,String p)throws Exception{Object x=o.get(k);if(!(x instanceof JSONObject))fail(p+"."+k);return (JSONObject)x;}
    private static JSONArray arr(JSONObject o,String k,String p,int min,int max)throws Exception{Object x=o.get(k);if(!(x instanceof JSONArray))fail(p+"."+k);JSONArray a=(JSONArray)x;if(a.length()<min||a.length()>max)fail(p+"."+k);return a;}
    private static String str(JSONObject o,String k,String p)throws Exception{Object x=o.get(k);if(!(x instanceof String)||((String)x).isEmpty())fail(p+"."+k);return (String)x;}
    private static void bool(JSONObject o,String k,String p)throws Exception{if(!(o.get(k) instanceof Boolean))fail(p+"."+k);}
    private static double number(JSONObject o,String k,String p,double min,double max,boolean integer)throws Exception{Object x=o.get(k);if(!(x instanceof Number))fail(p+"."+k);double n=((Number)x).doubleValue();if(!Double.isFinite(n)||n<min||n>max||(integer&&n!=Math.rint(n)))fail(p+"."+k);return n;}
    private static int integer(JSONObject o,String k,String p,int min,int max)throws Exception{return (int)number(o,k,p,min,max,true);}
    private static void quant(JSONObject o,String k,String p)throws Exception{int n=integer(o,k,p,0,16);if(n!=0&&n!=8&&n!=16)fail(p+"."+k);}
    private static JSONObject item(JSONArray a,int i,String p)throws Exception{Object x=a.get(i);if(!(x instanceof JSONObject))fail(p+"["+i+"]");return (JSONObject)x;}
    static ComposerProject parse(String text)throws Exception{
        if(text.startsWith("\uFEFF"))text=text.substring(1);
        // JsonReader rejects comments, unquoted names, duplicate keys and trailing content.
        try(android.util.JsonReader r=new android.util.JsonReader(new StringReader(text))){r.setLenient(false);scan(r,0);if(r.peek()!=android.util.JsonToken.END_DOCUMENT)fail("JSON trailing content");}catch(Invalid e){throw e;}catch(Exception e){throw new Invalid("JSON syntax");}
        JSONObject p;try{p=new JSONObject(text);}catch(Exception e){throw new Invalid("JSON syntax");}try{validate(p);return ComposerProject.read(p);}catch(Invalid e){throw e;}catch(JSONException e){throw new Invalid("Missing or invalid field: "+e.getMessage());}catch(IllegalArgumentException e){throw new Invalid("Invalid project structure");}
    }
    private static void scan(android.util.JsonReader r,int depth)throws Exception{if(depth>64)fail("JSON nesting");switch(r.peek()){
        case BEGIN_OBJECT:{r.beginObject();Set<String> keys=new HashSet<>();while(r.hasNext()){String k=r.nextName();if(!keys.add(k))fail("duplicate key: "+k);scan(r,depth+1);}r.endObject();break;}
        case BEGIN_ARRAY:r.beginArray();while(r.hasNext())scan(r,depth+1);r.endArray();break;
        case STRING:r.nextString();break;case NUMBER:r.nextString();break;case BOOLEAN:r.nextBoolean();break;case NULL:r.nextNull();break;default:fail("JSON syntax");}}
    static void validate(JSONObject p)throws Exception{
        keys(p,"project","version","id","name","key","scale","bpm","meter","seed","parts","order","voice");integer(p,"version","project",1,1);str(p,"id","project");str(p,"name","project");integer(p,"key","project",0,11);integer(p,"scale","project",0,4);integer(p,"bpm","project",40,240);int meter=integer(p,"meter","project",0,2);seed(p);
        JSONObject v=obj(p,"voice","project");keys(v,"voice","program","mode","octave","response","quality","quantize","autoRange","snap","rhythm","mixVoice","confidence","gate");integer(v,"program","voice",0,127);integer(v,"mode","voice",0,1);integer(v,"octave","voice",-3,3);integer(v,"response","voice",0,1);integer(v,"quality","voice",-1,10);quant(v,"quantize","voice");for(String k:new String[]{"autoRange","snap","rhythm","mixVoice"})bool(v,k,"voice");number(v,"confidence","voice",.5,.990001,false);number(v,"gate","voice",.001-1e-9,.100001,false);
        JSONArray parts=arr(p,"parts","project",1,8),order=arr(p,"order","project",1,64);Map<String,Integer> lengths=new HashMap<>();Set<String> allTracks=new HashSet<>();int notes=0,points=0;
        for(int i=0;i<parts.length();i++){JSONObject part=item(parts,i,"parts");String path="parts["+i+"]";keys(part,path,"id","label","bars","rhythm","energy","humanize","fill","chords","tracks","phrases");String id=str(part,"id",path);if(lengths.containsKey(id))fail(path+".id duplicate");int bars=integer(part,"bars",path,4,16);if(bars!=4&&bars!=8&&bars!=16)fail(path+".bars");lengths.put(id,bars);str(part,"label",path);int rhythm=integer(part,"rhythm",path,0,MusicTheory.RHYTHMS.length-1);if(!MusicTheory.rhythmFits(rhythm,meter))fail(path+".rhythm incompatible with meter");number(part,"energy",path,0,1,false);number(part,"humanize",path,0,1,false);bool(part,"fill",path);
            JSONArray chords=arr(part,"chords",path,bars,bars),tracks=arr(part,"tracks",path,0,8),phrases=arr(part,"phrases",path,0,100000);Map<String,Boolean> voices=new HashMap<>();
            for(int b=0;b<bars;b++){Object value=chords.get(b);if(!(value instanceof JSONArray))fail(path+".chords["+b+"]");JSONArray a=(JSONArray)value;if(a.length()<1||a.length()>2)fail(path+".chords["+b+"]");for(int c=0;c<a.length();c++){JSONObject ch=item(a,c,path+".chords["+b+"]");String q=path+".chords["+b+"]["+c+"]";keys(ch,q,"root","quality","bass","degree","automatic","seventh","dominant");integer(ch,"root",q,0,11);integer(ch,"quality",q,0,10);integer(ch,"bass",q,-1,11);integer(ch,"degree",q,0,6);for(String k:new String[]{"automatic","seventh","dominant"})bool(ch,k,q);}}
            for(int t=0;t<tracks.length();t++){JSONObject tr=item(tracks,t,path+".tracks");String q=path+".tracks["+t+"]";keys(tr,q,"id","name","program","bank","pattern","volume","pan","muted","voice","phraseSource");String tid=str(tr,"id",q);if(voices.containsKey(tid))fail(q+".id duplicate");str(tr,"name",q);integer(tr,"program",q,0,127);int bank=integer(tr,"bank",q,0,128);if(bank!=0&&bank!=128)fail(q+".bank");integer(tr,"pattern",q,0,7);number(tr,"volume",q,0,1,false);number(tr,"pan",q,-1,1,false);bool(tr,"muted",q);bool(tr,"voice",q);if(tr.has("phraseSource")){Object source=tr.get("phraseSource");if(!(source instanceof String)||!(source.equals("")||source.equals("microphone")||source.equals("keyboard")))fail(q+".phraseSource");}voices.put(tid,tr.getBoolean("voice"));allTracks.add(tid);}
            for(int f=0;f<phrases.length();f++){JSONObject ph=item(phrases,f,path+".phrases");String q=path+".phrases["+f+"]";keys(ph,q,"trackId","quantize","chordMode","notes");String tid=str(ph,"trackId",q);if(!Boolean.TRUE.equals(voices.get(tid)))fail(q+".trackId");quant(ph,"quantize",q);bool(ph,"chordMode",q);JSONArray ns=arr(ph,"notes",q,0,20000);notes+=ns.length();if(notes>100000)fail("notes total");
                for(int j=0;j<ns.length();j++){JSONObject n=item(ns,j,q+".notes");String z=q+".notes["+j+"]";keys(n,z,"beat","length","offset","midi","velocity","degree","quality","automatic","bend","expression","curve");number(n,"beat",z,0,1024,false);number(n,"length",z,.01,1024,false);number(n,"offset",z,0,1024,false);integer(n,"midi",z,0,127);integer(n,"velocity",z,1,127);integer(n,"degree",z,-1,6);integer(n,"quality",z,0,10);bool(n,"automatic",z);number(n,"bend",z,-12800,12800,false);number(n,"expression",z,0,1,false);JSONArray curve=arr(n,"curve",z,0,12000);points+=curve.length();if(points>200000)fail("curve points total");double prev=-1;
                    for(int x=0;x<curve.length();x++){Object cp=curve.get(x);if(!(cp instanceof JSONArray)||((JSONArray)cp).length()!=3)fail(z+".curve["+x+"]");JSONArray a=(JSONArray)cp;for(int k=0;k<3;k++)if(!(a.get(k) instanceof Number)||!Double.isFinite(((Number)a.get(k)).doubleValue()))fail(z+".curve["+x+"]");double at=a.getDouble(0),bend=a.getDouble(1),expr=a.getDouble(2);if(at<prev||at<0||at>1024||bend< -12800||bend>12800||expr<0||expr>1)fail(z+".curve["+x+"]");prev=at;}
                }
            }
        }
        if(allTracks.size()>8)fail("tracks total");int total=0;for(int i=0;i<order.length();i++){JSONObject o=item(order,i,"order");String q="order["+i+"]";keys(o,q,"part","repeats");String id=str(o,"part",q);if(!lengths.containsKey(id))fail(q+".part");total+=lengths.get(id)*integer(o,"repeats",q,1,64);if(total>256)fail("order total bars");}
    }
}
