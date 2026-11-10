package pro.livemic.studio;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

/** One repair queue per directory, nonblocking per-take leases, and shared completion barriers. */
final class TakeRecovery {
    private static final ExecutorService WORKER=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"take-recovery");t.setDaemon(true);return t;});
    private static final Map<String,Batch> BATCHES=new HashMap<>();
    private static final ConcurrentHashMap<String,ReentrantLock> LOCKS=new ConcurrentHashMap<>();
    private static final Set<String> RESERVED=ConcurrentHashMap.newKeySet();
    private static final class Batch {
        final CompletableFuture<Void> done=new CompletableFuture<>();final Set<String> queued=new HashSet<>();final Map<String,String> errors=new LinkedHashMap<>();boolean rescan;
    }
    private static String directory(TakeStore store){return store.directory.getAbsolutePath();}
    private static String key(TakeStore store,String id){return directory(store)+"/"+id;}
    private static ReentrantLock lock(TakeStore store,String id){return LOCKS.computeIfAbsent(key(store,id),unused->new ReentrantLock());}
    static final class Lease implements AutoCloseable {private final ReentrantLock lock;Lease(ReentrantLock lock){this.lock=lock;}public void close(){lock.unlock();}}
    static Lease lease(TakeStore store,String id)throws IOException{
        ReentrantLock lock=lock(store,id);
        if(RESERVED.contains(key(store,id))&&!lock.isHeldByCurrentThread()||!lock.tryLock())throw new IOException("This take is being recovered or edited");return new Lease(lock);
    }
    static boolean busy(TakeStore store,String id){ReentrantLock lock=lock(store,id);return RESERVED.contains(key(store,id))||lock.isLocked()&&!lock.isHeldByCurrentThread();}
    static boolean recovering(TakeStore store){synchronized(BATCHES){Batch batch=BATCHES.get(directory(store));return batch!=null&&!batch.done.isDone();}}
    static String failure(TakeStore store){synchronized(BATCHES){Batch batch=BATCHES.get(directory(store));return batch==null?"":String.join("\n",batch.errors.values());}}
    static void await(TakeStore store)throws Exception{
        Batch batch;synchronized(BATCHES){batch=BATCHES.get(directory(store));}
        if(batch!=null&&!batch.done.isDone()){if("take-recovery".equals(Thread.currentThread().getName()))throw new IllegalStateException("Recovery cannot wait on itself");batch.done.get();}
    }
    static void request(TakeStore store,String id,Executor completionExecutor,Runnable completed){
        String directory=directory(store);Batch batch;boolean launch=false;
        synchronized(BATCHES){batch=BATCHES.get(directory);if(batch==null||batch.done.isDone()){batch=new Batch();BATCHES.put(directory,batch);launch=true;}else batch.rescan=true;
            if(id!=null){batch.queued.add(id);RESERVED.add(key(store,id));}
        }
        Batch requested=batch;
        if(completed!=null)requested.done.whenComplete((ignored,error)->{try{completionExecutor.execute(completed);}catch(RejectedExecutionException ignoredExit){}});
        if(launch)WORKER.execute(()->run(store,requested));
    }
    private static void run(TakeStore store,Batch batch){
        for(;;){
            Set<String> ids=new HashSet<>();synchronized(BATCHES){batch.rescan=false;ids.addAll(batch.queued);batch.queued.clear();}
            try{
                File[] files=store.directory.listFiles();if(files==null)throw new IOException("Cannot scan recorded files");
                for(File file:files){String name=file.getName();if(name.endsWith(".recovery-original"))name=name.substring(0,name.length()-18);
                    if(name.endsWith(".wav")&&!name.endsWith(".raw.wav")&&!name.endsWith(".fixed.wav")&&!name.endsWith(".vocal.wav"))ids.add(name.substring(0,name.length()-4));}
                ids.removeIf(id->!id.matches("[A-Za-z0-9_-]+")||RecordingManager.isWritingId(id)||store.file(id,".json").exists()&&!RecordingIntegrity.pending(store,id));
                for(String id:ids)RESERVED.add(key(store,id));
                for(String id:ids){
                    if(ExitCoordinator.isExitRequested())break;
                    ReentrantLock lock=lock(store,id);boolean acquired=lock.tryLock();
                    try{
                        if(!acquired)throw new IOException("Take is in use; retry recovery after editing");
                        if(RecordingManager.isWritingId(id))continue;
                        store.recoverOne(id);synchronized(BATCHES){batch.errors.remove(id);}
                    }catch(Exception failure){synchronized(BATCHES){batch.errors.put(id,id+": "+(failure.getMessage()==null?failure.toString():failure.getMessage()));}}
                    finally{if(acquired)lock.unlock();synchronized(BATCHES){if(!batch.queued.contains(id))RESERVED.remove(key(store,id));}}
                }
            }catch(Exception failure){synchronized(BATCHES){batch.errors.put("scan",failure.getMessage()==null?failure.toString():failure.getMessage());}}
            synchronized(BATCHES){if((batch.rescan||!batch.queued.isEmpty())&&!ExitCoordinator.isExitRequested())continue;
                RESERVED.removeIf(id->id.startsWith(directory(store)+"/"));batch.done.complete(null);return;}
        }
    }
}
