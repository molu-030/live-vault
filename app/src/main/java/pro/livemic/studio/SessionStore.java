package pro.livemic.studio;

import android.content.Context;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.util.*;

/** Session-owned manifests and copied media. Never deletes or rewrites a legacy source. */
final class SessionStore {
    final File directory;
    SessionStore(Context c){directory=new File(c.getFilesDir(),"sessions");}
    SessionStore(File root){directory=root;}
    private File folder(String id)throws IOException{if(!SessionProject.safeId(id))throw new IOException("Invalid session ID");File f=new File(directory,id);if(!f.getCanonicalFile().getParentFile().equals(directory.getCanonicalFile()))throw new IOException("Invalid session directory");return f;}
    private File manifest(String id)throws IOException{return new File(folder(id),"project.json");}
    private static void mkdir(File f)throws IOException{if(!f.isDirectory()&&!f.mkdirs())throw new IOException("Cannot create session directory");}
    synchronized SessionProject create(String name)throws Exception{SessionProject p=new SessionProject();p.name=name;save(p);return p;}
    synchronized List<SessionProject> list(){List<SessionProject> out=new ArrayList<>();File[] entries=directory.listFiles();if(entries!=null)for(File f:entries)if(f.isDirectory())try{out.add(open(f.getName()));}catch(Exception ignored){}out.sort((a,b)->Long.compare(b.modified,a.modified));return out;}
    synchronized SessionProject open(String id)throws Exception{
        byte[] bytes=new AtomicFile(manifest(id)).readFully();if(bytes.length>4*1024*1024)throw new IOException("Session manifest too large");SessionProject p=SessionProject.read(new JSONObject(new String(bytes,StandardCharsets.UTF_8)));if(!id.equals(p.id))throw new IOException("Session ID mismatch");
        // Missing media remains visible to the controller; existing malformed or changed media is rejected.
        for(SessionProject.Media m:p.media){File f=mediaFile(p,m);if(f.exists())verifyMedia(f,m);}return p;
    }
    synchronized void save(SessionProject p)throws Exception{save(p,false);}
    /** Bounded metadata drain only: preserves accepted edits and capture anchors after exit admission closes. */
    synchronized void saveFinalization(SessionProject p)throws Exception{save(p,true);}
    private void save(SessionProject p,boolean allowExit)throws Exception{
        checkMetadataAllowed(allowExit);p.validate();File dir=folder(p.id);mkdir(new File(dir,"media"));for(SessionProject.Media m:p.media){checkMetadataAllowed(allowExit);File f=mediaFile(p,m);if(f.exists())verifyMedia(f,m);}
        long old=p.modified;p.modified=System.currentTimeMillis();AtomicFile a=new AtomicFile(manifest(p.id));FileOutputStream out=null;
        try{byte[] bytes=p.json().toString(2).getBytes(StandardCharsets.UTF_8);if(bytes.length>4*1024*1024)throw new IOException("Session manifest too large");checkMetadataAllowed(allowExit);out=a.startWrite();out.write(bytes);out.getFD().sync();checkMetadataAllowed(allowExit);a.finishWrite(out);out=null;syncDirectory(dir);syncDirectory(directory);}catch(Exception e){p.modified=old;if(out!=null)a.failWrite(out);throw e;}
    }
    synchronized void rename(SessionProject p,String name)throws Exception{String old=p.name;p.name=name;try{save(p);}catch(Exception e){p.name=old;throw e;}}
    synchronized SessionProject duplicate(SessionProject source,String name)throws Exception{
        SessionProject p=SessionProject.read(source.json());p.id=UUID.randomUUID().toString();p.name=name;p.created=p.modified=System.currentTimeMillis();p.pendingTarget=null;
        p.validate();try{for(SessionProject.Media m:p.media){File src=mediaFile(source,m),dst=mediaFile(p,m);mkdir(dst.getParentFile());copy(src,dst);verifyMedia(dst,m);}checkCopyAllowed();save(p);return p;}catch(Exception e){try{delete(p.id);}catch(Exception cleanup){e.addSuppressed(cleanup);}throw e;}
    }
    synchronized void delete(String id)throws IOException{File dir=folder(id);if(Files.isSymbolicLink(dir.toPath()))throw new IOException("Refusing linked session directory");deleteTree(dir,dir.getCanonicalFile());}
    private static void deleteTree(File file,File root)throws IOException{
        if(!file.exists())return;if(Files.isSymbolicLink(file.toPath())){Files.delete(file.toPath());return;}
        File canonical=file.getCanonicalFile();if(!canonical.equals(root)&&!canonical.toPath().startsWith(root.toPath()))throw new IOException("Invalid session child");File[] children=file.listFiles();if(children!=null)for(File child:children)deleteTree(child,root);if(!file.delete())throw new IOException("Cannot delete session file");
    }
    File mediaFile(SessionProject p,SessionProject.Media m)throws IOException{
        if(m.file==null||!m.file.matches("media/[A-Za-z0-9_-]{1,96}\\.wav"))throw new IOException("Invalid media path");File root=folder(p.id).getCanonicalFile(),media=new File(root,"media");if(Files.isSymbolicLink(media.toPath()))throw new IOException("Linked media directory");File f=new File(root,m.file);if(!f.getCanonicalFile().getParentFile().equals(media.getCanonicalFile()))throw new IOException("Invalid media location");return f;
    }
    /** Validates the app's exact canonical 44 byte PCM16/float32 WAV layout. */
    static SessionProject.Media inspectWav(File file)throws IOException{
        try(RandomAccessFile in=new RandomAccessFile(file,"r")){
            if(in.length()<44)throw new IOException("Truncated WAV");byte[] h=new byte[44];in.readFully(h);ByteBuffer b=ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN);
            if(!tag(h,0,"RIFF")||!tag(h,8,"WAVE")||!tag(h,12,"fmt ")||!tag(h,36,"data")||b.getInt(16)!=16)throw new IOException("Unsupported WAV header");
            SessionProject.Media m=new SessionProject.Media();m.format=Short.toUnsignedInt(b.getShort(20));m.channels=Short.toUnsignedInt(b.getShort(22));m.sampleRate=b.getInt(24);m.bits=Short.toUnsignedInt(b.getShort(34));
            if(m.sampleRate<8000||m.sampleRate>192000||(m.channels!=1&&m.channels!=2)||!((m.format==1&&m.bits==16)||(m.format==3&&m.bits==32)))throw new IOException("Unsupported WAV format");int block=m.channels*m.bits/8;long data=Integer.toUnsignedLong(b.getInt(40));
            if(Short.toUnsignedInt(b.getShort(32))!=block||Integer.toUnsignedLong(b.getInt(28))!=(long)m.sampleRate*block||data==0||data%block!=0||in.length()!=44+data||Integer.toUnsignedLong(b.getInt(4))!=36+data)throw new IOException("Invalid WAV size or alignment");m.frames=data/block;if(SessionProject.sourceToProject(m.frames,m.sampleRate)>SessionProject.MAX_FRAMES)throw new IOException("WAV too long");return m;
        }
    }
    private static boolean tag(byte[] bytes,int offset,String s){for(int i=0;i<s.length();i++)if(bytes[offset+i]!=(byte)s.charAt(i))return false;return true;}
    private static void checkMetadataAllowed(boolean allowExit)throws IOException{if(Thread.currentThread().isInterrupted()||(!allowExit&&ExitCoordinator.isExitRequested()))throw new InterruptedIOException("Session metadata write cancelled");}
    private static void verifyMedia(File file,SessionProject.Media expected)throws IOException{SessionProject.Media m=inspectWav(file);if(m.frames!=expected.frames||m.sampleRate!=expected.sampleRate||m.channels!=expected.channels||m.format!=expected.format||m.bits!=expected.bits)throw new IOException("Session media differs from manifest");}
    private static void syncDirectory(File directory)throws IOException{try(FileChannel channel=FileChannel.open(directory.toPath(),StandardOpenOption.READ)){channel.force(true);}}
    private static void checkCopyAllowed()throws IOException{if(Thread.currentThread().isInterrupted()||ExitCoordinator.isExitRequested())throw new InterruptedIOException("Session import cancelled");}
    private static void copy(File source,File destination)throws IOException{
        File pending=new File(destination.getParentFile(),destination.getName()+".pending");
        try{checkCopyAllowed();try(FileInputStream in=new FileInputStream(source);FileOutputStream out=new FileOutputStream(pending)){byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))!=-1){checkCopyAllowed();out.write(buffer,0,n);}out.getFD().sync();}
            checkCopyAllowed();try{Files.move(pending.toPath(),destination.toPath(),StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){checkCopyAllowed();Files.move(pending.toPath(),destination.toPath());}syncDirectory(destination.getParentFile());
        }finally{Files.deleteIfExists(pending.toPath());}
    }
    synchronized SessionProject.Media importWav(SessionProject p,File source,String kind,String sourceId)throws Exception{
        p.validate();if(p.media.size()>=128)throw new IOException("Media limit exceeded");SessionProject.Media m=inspectWav(source);m.file="media/"+m.id+".wav";m.sourceKind=kind;m.sourceId=sourceId;File dest=mediaFile(p,m);mkdir(dest.getParentFile());copy(source,dest);verifyMedia(dest,m);checkCopyAllowed();p.media.add(m);return m;
    }
    synchronized SessionProject.Clip addAudioClip(SessionProject p,SessionProject.Media m,String name,String kind,long startFrame)throws Exception{
        if(p.tracks.size()>=SessionProject.MAX_AUDIO_TRACKS||p.clips.size()>=SessionProject.MAX_CLIPS)throw new IOException("Track or clip limit exceeded");if(p.media(m.id)!=m)throw new IOException("Media is not in this session");SessionProject.Track t=new SessionProject.Track();t.name=name;t.kind=kind;SessionProject.Clip c=new SessionProject.Clip();c.trackId=t.id;c.mediaId=m.id;c.startFrame=startFrame;c.lengthFrames=SessionProject.sourceToProject(m.frames,m.sampleRate);p.tracks.add(t);p.clips.add(c);try{p.validate();}catch(Exception e){p.tracks.remove(t);p.clips.remove(c);throw e;}return c;
    }
    synchronized void importComposer(SessionProject p,ComposerProject composer)throws Exception{ComposerProject prior=p.composer;p.composer=ComposerProject.read(composer.json());try{p.validate();}catch(Exception e){p.composer=prior;throw e;}}
    synchronized void importHarmony(SessionProject p,HarmonySession source)throws Exception{
        if(!source.ready||source.busy||source.state[4]>=0||source.state[5]>=0)throw new IOException("Finish harmony preparation or recording first");int count=0;for(String f:source.files)if(f!=null)count++;if(p.tracks.size()+count>SessionProject.MAX_AUDIO_TRACKS||p.clips.size()+count>SessionProject.MAX_CLIPS)throw new IOException("Track or clip limit exceeded");
        int nm=p.media.size(),nt=p.tracks.size(),nc=p.clips.size();JSONObject old=p.harmony;
        try{p.harmony=new JSONObject(source.json().toString());for(int i=0;i<4;i++)if(source.files[i]!=null){if(!source.files[i].matches("[A-Za-z0-9_-]+\\.wav"))throw new IOException("Invalid harmony media filename");SessionProject.Media m=importWav(p,new File(source.dir,source.files[i]),"harmony",source.files[i]);SessionProject.Clip c=addAudioClip(p,m,source.partName(i),"HARMONY",0);repeatHarmony(p,c);SessionProject.Track t=p.track(c.trackId);t.gain=source.volume[i];t.pan=source.pan[i];t.muted=source.muted[i];}p.validate();}
        catch(Exception e){while(p.clips.size()>nc)p.clips.remove(p.clips.size()-1);while(p.tracks.size()>nt)p.tracks.remove(p.tracks.size()-1);while(p.media.size()>nm)p.media.remove(p.media.size()-1);p.harmony=old;throw e;}
    }
    synchronized SessionProject.Clip importTake(SessionProject p,TakeStore source,String id,long startFrame)throws Exception{
        return importTake(p,source,id,startFrame,true);
    }
    synchronized SessionProject.Clip importTake(SessionProject p,TakeStore source,String id,long startFrame,boolean vocal)throws Exception{
        return importTake(p,source,id,startFrame,vocal,null);
    }
    synchronized SessionProject.Clip importTakeToTrack(SessionProject p,TakeStore source,String id,String trackId,long startFrame)throws Exception{
        if(p.track(trackId)==null)throw new IOException("Recording track is unavailable");return importTake(p,source,id,startFrame,true,trackId);
    }
    private SessionProject.Clip importTake(SessionProject p,TakeStore source,String id,long startFrame,boolean vocal,String targetTrack)throws Exception{
        if(!SessionProject.safeId(id))throw new IOException("Invalid take ID");
        try(TakeRecovery.Lease ignored=source.lease(id)){
            if(RecordingManager.isWritingId(id)||source.isRecovering(id))throw new IOException("Take is still being saved");JSONObject meta=source.read(id);if(meta.optBoolean("integrityRepairPending")||RecordingIntegrity.pending(source,id))throw new IOException("Take recovery is not complete");File file=source.file(id,".wav");String kind="AUDIO";
            if(vocal&&meta.optBoolean("sourceVocalRetained")){File stem=source.file(id,".vocal.wav");try{inspectWav(stem);file=stem;kind="VOCAL";}catch(IOException unavailableStem){}}
            int size=p.media.size(),clips=p.clips.size();try{SessionProject.Media m=importWav(p,file,"take",id);if(targetTrack==null)return addAudioClip(p,m,meta.optString("name","Take"),kind,startFrame);if(p.clips.size()>=SessionProject.MAX_CLIPS)throw new IOException("Clip limit exceeded");SessionProject.Clip c=new SessionProject.Clip();c.trackId=targetTrack;c.mediaId=m.id;c.startFrame=startFrame;c.lengthFrames=SessionProject.sourceToProject(m.frames,m.sampleRate);p.clips.add(c);p.validate();return c;}catch(Exception e){while(p.clips.size()>clips)p.clips.remove(p.clips.size()-1);while(p.media.size()>size)p.media.remove(p.media.size()-1);throw e;}
        }
    }
    static void repeatHarmony(SessionProject p,SessionProject.Clip c){c.loop=true;c.loopLengthFrames=c.lengthFrames;c.lengthFrames=Math.max(c.lengthFrames,p.durationFrames());}
    /** Caller supplies token/sessionId/trackId/startFrame; a failed write preserves the previous journal. */
    synchronized void writePendingTarget(SessionProject p,JSONObject target)throws Exception{writePendingTarget(p,target,false);}
    synchronized void writePendingTargetFinalization(SessionProject p,JSONObject target)throws Exception{writePendingTarget(p,target,true);}
    private void writePendingTarget(SessionProject p,JSONObject target,boolean allowExit)throws Exception{JSONObject previous=p.pendingTarget;p.pendingTarget=target==null?null:new JSONObject(target.toString());try{save(p,allowExit);}catch(Exception e){p.pendingTarget=previous;throw e;}}
}
