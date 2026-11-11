package pro.livemic.studio;
import android.content.Context;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Atomic replacement; an interrupted save leaves the last complete JSON recoverable. */
final class ComposerStore {
    final File directory;final ProjectVersions versions;final ArrayDeque<String> undo=new ArrayDeque<>(),redo=new ArrayDeque<>();
    ComposerStore(Context c){directory=new File(c.getFilesDir(),"composer");directory.mkdirs();versions=new ProjectVersions(directory);}
    private File file(String id){if(!id.matches("[a-zA-Z0-9-]+"))throw new IllegalArgumentException("프로젝트 ID");return new File(directory,id+".json");}
    synchronized void write(ComposerProject p,boolean draft)throws Exception{AtomicFile a=new AtomicFile(draft?new File(directory,"draft.json"):file(p.id));FileOutputStream out=a.startWrite();try{out.write(p.json().toString(2).getBytes(StandardCharsets.UTF_8));a.finishWrite(out);}catch(Exception e){a.failWrite(out);throw e;}}
    synchronized ComposerProject read(File f)throws Exception{return ComposerProject.read(new JSONObject(new String(new AtomicFile(f).readFully(),StandardCharsets.UTF_8)));}
    synchronized long savedAt(String id,boolean draft){return (draft?new File(directory,"draft.json"):file(id)).lastModified();}
    synchronized ComposerProject draft(){try{return read(new File(directory,"draft.json"));}catch(Exception e){return null;}}
    synchronized List<ComposerProject> list(){List<ComposerProject> out=new ArrayList<>();File[] files=directory.listFiles((d,n)->n.endsWith(".json")&&!n.equals("draft.json"));if(files!=null){Arrays.sort(files,(a,b)->Long.compare(b.lastModified(),a.lastModified()));for(File f:files)try{out.add(read(f));}catch(Exception ignored){}}return out;}
    synchronized void delete(String id)throws IOException{versions.deleteProject(id);File saved=file(id);new AtomicFile(saved).delete();if(saved.exists())throw new IOException("Cannot delete project");}
    void checkpoint(ComposerProject p)throws JSONException{undo.addLast(p.json().toString());while(undo.size()>20)undo.removeFirst();redo.clear();}
    ComposerProject history(ComposerProject p,boolean forward)throws JSONException{ArrayDeque<String> from=forward?redo:undo,to=forward?undo:redo;if(from.isEmpty())return p;ComposerProject restored=ComposerProject.read(new JSONObject(from.peekLast()));String current=p.json().toString();from.removeLast();to.addLast(current);return restored;}
}
