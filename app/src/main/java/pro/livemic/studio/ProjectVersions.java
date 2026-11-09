package pro.livemic.studio;

import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Local history is separate from project v1 JSON and the temporary undo stack. */
final class ProjectVersions {
    static final int LIMIT=20;
    final File directory;
    static final class Version {
        final String projectId,id,name; final long createdAt;
        Version(String projectId,String id,String name,long createdAt){this.projectId=projectId;this.id=id;this.name=name;this.createdAt=createdAt;}
    }
    ProjectVersions(File composerDirectory){directory=new File(composerDirectory,"versions");}
    private static void valid(String id){if(id==null||!id.matches("[a-zA-Z0-9-]+"))throw new IllegalArgumentException("Invalid history ID");}
    private File folder(String projectId)throws IOException{valid(projectId);File folder=new File(directory,projectId);if(!folder.isDirectory()&&!folder.mkdirs())throw new IOException("Cannot create version history");return folder;}
    private File snapshot(String projectId,String id)throws IOException{valid(id);return new File(folder(projectId),id+".json");}
    private File metadata(String projectId,String id)throws IOException{valid(id);return new File(folder(projectId),id+".meta.json");}
    private static String read(File file)throws IOException{return new String(new AtomicFile(file).readFully(),StandardCharsets.UTF_8);}
    private static void write(File file,String text)throws IOException{
        AtomicFile atomic=new AtomicFile(file);FileOutputStream out=atomic.startWrite();
        try{out.write(text.getBytes(StandardCharsets.UTF_8));atomic.finishWrite(out);}catch(Exception e){atomic.failWrite(out);throw e;}
    }
    private void metadata(Version version)throws Exception{
        write(metadata(version.projectId,version.id),new JSONObject().put("id",version.id).put("name",version.name).put("createdAt",version.createdAt).toString());
    }
    synchronized List<Version> list(String projectId)throws Exception{
        File projectFolder=folder(projectId);cleanupAbandoned(projectFolder);
        File[] files=projectFolder.listFiles((d,n)->n.endsWith(".meta.json"));
        if(files==null)throw new IOException("Cannot read version history");
        List<Version> versions=new ArrayList<>();
        for(File file:files){
            // A damaged entry must not hide healthy, independently atomic snapshots.
            try{JSONObject meta=new JSONObject(read(file));String id=meta.getString("id");
                if(!file.getName().equals(id+".meta.json"))continue;
                ComposerProject project=readProject(projectId,id);
                if(!project.id.equals(projectId))continue;
                versions.add(new Version(projectId,id,meta.getString("name"),meta.getLong("createdAt")));
            }catch(Exception ignored){}
        }
        versions.sort((a,b)->{int stamp=Long.compare(b.createdAt,a.createdAt);return stamp!=0?stamp:b.id.compareTo(a.id);});return versions;
    }
    synchronized ComposerProject readProject(String projectId,String versionId)throws Exception{
        ComposerProject project=ComposerProject.read(new JSONObject(read(snapshot(projectId,versionId))));
        if(!project.id.equals(projectId))throw new IOException("Version belongs to a different project");return project;
    }
    synchronized Version stage(ComposerProject project,String name)throws Exception{
        project.validate();String id=UUID.randomUUID().toString();
        long stamp=System.currentTimeMillis();List<Version> previous=list(project.id);
        if(!previous.isEmpty())stamp=Math.max(stamp,previous.get(0).createdAt+1);
        Version version=new Version(project.id,id,cleanName(name),stamp);
        File data=snapshot(project.id,id);write(data,project.json().toString());
        try{write(new File(folder(project.id),id+".pending"),new JSONObject().put("name",version.name).put("createdAt",stamp).toString());}
        catch(Exception e){new AtomicFile(data).delete();throw e;}
        return version;
    }
    synchronized void commitStaged(Version version)throws Exception{
        File pending=new File(folder(version.projectId),version.id+".pending");
        if(!pending.isFile())throw new IOException("Pending version is unavailable");
        readProject(version.projectId,version.id);
        metadata(version); // No existing history is pruned until this atomic write succeeds.
        new AtomicFile(pending).delete();
        // Once published, retention cleanup cannot turn a successful commit into a failed restore.
        try{List<Version> all=list(version.projectId);for(int i=LIMIT;i<all.size();i++)remove(all.get(i));}catch(Exception ignored){}
    }
    synchronized void discardStaged(Version version)throws IOException{
        File pending=new File(folder(version.projectId),version.id+".pending");
        if(!metadata(version.projectId,version.id).exists()){new AtomicFile(pending).delete();new AtomicFile(snapshot(version.projectId,version.id)).delete();}
    }
    private void remove(Version version)throws IOException{
        new AtomicFile(metadata(version.projectId,version.id)).delete();new AtomicFile(snapshot(version.projectId,version.id)).delete();
    }
    synchronized Version create(ComposerProject project,String name)throws Exception{
        Version version=stage(project,name);
        try{commitStaged(version);return version;}catch(Exception e){discardStaged(version);throw e;}
    }
    synchronized void deleteProject(String projectId)throws IOException{valid(projectId);deleteTree(new File(directory,projectId));}
    private static void deleteTree(File file)throws IOException{
        if(!file.exists())return;
        if(file.isDirectory()){File[] files=file.listFiles();if(files==null)throw new IOException("Cannot read version history");for(File child:files)deleteTree(child);}
        if(!file.delete())throw new IOException("Cannot delete version history");
    }
    private void cleanupAbandoned(File folder){
        File[] files=folder.listFiles();if(files==null)return;long cutoff=System.currentTimeMillis()-86400000L;
        for(File file:files){String name=file.getName();String id=null;
            if(name.endsWith(".pending"))id=name.substring(0,name.length()-8);
            else if(name.endsWith(".json")&&!name.endsWith(".meta.json"))id=name.substring(0,name.length()-5);
            if(id==null||!id.matches("[a-zA-Z0-9-]+")||file.lastModified()>=cutoff)continue;
            File meta=new File(folder,id+".meta.json"),pending=new File(folder,id+".pending");
            if(!meta.exists()&&(!pending.exists()||pending.lastModified()<cutoff)){
                new AtomicFile(pending).delete();new AtomicFile(new File(folder,id+".json")).delete();
            }
        }
    }
    synchronized Version rename(Version version,String name)throws Exception{
        readProject(version.projectId,version.id); // Never rename a missing or unrelated snapshot.
        JSONObject existing=new JSONObject(read(metadata(version.projectId,version.id)));
        if(!version.id.equals(existing.getString("id")))throw new IOException("Version is unavailable");
        Version renamed=new Version(version.projectId,version.id,cleanName(name),existing.getLong("createdAt"));metadata(renamed);return renamed;
    }
    private static String cleanName(String name){String cleaned=name==null?"":name.trim();if(cleaned.isEmpty()||cleaned.length()>80)throw new IllegalArgumentException("Version name must contain 1–80 characters");return cleaned;}
}
