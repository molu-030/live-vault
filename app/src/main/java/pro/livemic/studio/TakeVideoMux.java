package pro.livemic.studio;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntConsumer;

/** Replaces only the audio track. Camera PTS already include the original vocal delay. */
final class TakeVideoMux {
    static void check(AtomicBoolean cancelled)throws IOException {
        if(cancelled.get()||ExitCoordinator.isExitRequested())throw new IOException("Cancelled");
    }
    static long audioTime(long encodedTime,long encodedFirst,long cameraAudioOffsetUs){
        return Math.addExact(Math.subtractExact(encodedTime,encodedFirst),cameraAudioOffsetUs);
    }
    static final class Samples {
        long count,first=-1,last=-1;
        void add(long time){if(count++==0)first=time;last=Math.max(last,time);}
    }
    static void verifySamples(Samples expected,Samples actual)throws IOException {
        if(expected.count==0||expected.count!=actual.count||Math.abs(expected.first-actual.first)>100||Math.abs(expected.last-actual.last)>100)
            throw new IOException("Video/audio sample timeline changed during muxing");
    }
    static long wavFrames(File wav)throws IOException {
        try(RandomAccessFile in=new RandomAccessFile(wav,"r")){
            if(in.length()<44||in.readInt()!=0x52494646)throw new IOException("Invalid WAV");
            in.seek(8);if(in.readInt()!=0x57415645||in.readInt()!=0x666d7420)throw new IOException("Unsupported WAV header");
            if(Integer.reverseBytes(in.readInt())!=16||Short.reverseBytes(in.readShort())!=1)throw new IOException("Video export requires PCM16 WAV");
            int channels=Short.toUnsignedInt(Short.reverseBytes(in.readShort()));int rate=Integer.reverseBytes(in.readInt());
            in.seek(32);int align=Short.toUnsignedInt(Short.reverseBytes(in.readShort())),bits=Short.toUnsignedInt(Short.reverseBytes(in.readShort()));
            if(channels!=2||rate<8000||rate>192000||bits!=16||align!=4)throw new IOException("Unsupported video WAV format");
            in.seek(36);if(in.readInt()!=0x64617461)throw new IOException("Unsupported WAV data chunk");
            long bytes=Integer.toUnsignedLong(Integer.reverseBytes(in.readInt()));
            if(bytes==0||bytes%align!=0||bytes!=in.length()-44)throw new IOException("Incomplete WAV");
            return bytes/align;
        }
    }
    private static int wavRate(File wav)throws IOException {try(RandomAccessFile in=new RandomAccessFile(wav,"r")){in.seek(24);return Integer.reverseBytes(in.readInt());}}
    static void validateWavs(File original,File edited)throws IOException {
        long source=wavFrames(original),target=wavFrames(edited);
        if(wavRate(original)!=wavRate(edited)||source!=target)throw new IOException("Edited audio duration differs from the original WAV");
    }
    static int rotation(File video)throws Exception {
        MediaMetadataRetriever r=new MediaMetadataRetriever();
        try{r.setDataSource(video.getAbsolutePath());String text=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
            int value=text==null?0:Integer.parseInt(text);if(value!=0&&value!=90&&value!=180&&value!=270)throw new IOException("Unsupported video rotation");return value;
        }finally{r.release();}
    }
    private static int track(MediaExtractor e,String prefix)throws IOException {
        int found=-1;
        for(int i=0;i<e.getTrackCount();i++)if(e.getTrackFormat(i).getString(MediaFormat.KEY_MIME).startsWith(prefix)){
            if(found!=-1)throw new IOException("Multiple tracks of the same type are not supported");found=i;}
        if(found<0)throw new IOException("Missing "+prefix+" track");return found;
    }
    private static Samples copy(MediaExtractor e,int source,MediaMuxer mux,int target,boolean audio,long offset,AtomicBoolean cancelled)throws Exception {
        e.selectTrack(source);e.seekTo(0,MediaExtractor.SEEK_TO_PREVIOUS_SYNC);ByteBuffer buffer=ByteBuffer.allocateDirect(4*1024*1024);MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
        Samples result=new Samples();long first=e.getSampleTime();
        while(true){
            check(cancelled);buffer.clear();int count=e.readSampleData(buffer,0);if(count<0)break;
            if(count>=buffer.capacity())throw new IOException("Video sample exceeds mux buffer");
            long time=audio?audioTime(e.getSampleTime(),first,offset):e.getSampleTime();
            if(time>=0){info.set(0,count,time,(e.getSampleFlags()&MediaExtractor.SAMPLE_FLAG_SYNC)!=0?MediaCodec.BUFFER_FLAG_KEY_FRAME:0);
                mux.writeSampleData(target,buffer,info);result.add(time);}
            else if(!audio)throw new IOException("Negative video timestamp");
            e.advance();
        }
        if(result.count==0)throw new IOException("Empty video/audio track");return result;
    }
    private static Samples samples(MediaExtractor e,int track,AtomicBoolean cancelled)throws Exception {
        e.selectTrack(track);e.seekTo(0,MediaExtractor.SEEK_TO_PREVIOUS_SYNC);Samples result=new Samples();while(e.getSampleTime()>=0){check(cancelled);result.add(e.getSampleTime());if(!e.advance())break;}e.unselectTrack(track);return result;
    }
    static void render(File sourceVideo,File originalWav,File editedWav,File output,long cameraAudioOffsetUs,AtomicBoolean cancelled,IntConsumer progress)throws Exception {
        validateWavs(originalWav,editedWav);check(cancelled);
        File encoded=new File(output.getParentFile(),output.getName()+".aac.m4a");
        MediaExtractor video=new MediaExtractor(),audio=new MediaExtractor();MediaMuxer mux=null;
        try{
            progress.accept(1);MediaTools.encodeAac(editedWav,encoded,cancelled);check(cancelled);
            video.setDataSource(sourceVideo.getAbsolutePath());audio.setDataSource(encoded.getAbsolutePath());
            int vi=track(video,"video/"),originalAudio=track(video,"audio/"),ai=track(audio,"audio/");
            // The signed clock offset is required: a negative value originally trimmed audio.
            video.selectTrack(originalAudio);video.seekTo(0,MediaExtractor.SEEK_TO_PREVIOUS_SYNC);long originalAudioFirst=video.getSampleTime();video.unselectTrack(originalAudio);
            if(originalAudioFirst<0)throw new IOException("Original video has no audio samples");
            int orientation=rotation(sourceVideo);MediaFormat originalVideo=video.getTrackFormat(vi);
            mux=new MediaMuxer(output.getAbsolutePath(),MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);mux.setOrientationHint(orientation);
            int vt=mux.addTrack(originalVideo),at=mux.addTrack(audio.getTrackFormat(ai));mux.start();
            progress.accept(2);Samples expectedVideo=copy(video,vi,mux,vt,false,0,cancelled);
            progress.accept(3);Samples expectedAudio=copy(audio,ai,mux,at,true,cameraAudioOffsetUs,cancelled);
            mux.stop();mux.release();mux=null;check(cancelled);
            progress.accept(4);MediaExtractor verify=new MediaExtractor();
            try{
                verify.setDataSource(output.getAbsolutePath());int v=track(verify,"video/"),a=track(verify,"audio/");
                if(verify.getTrackCount()!=2)throw new IOException("Unexpected output track count");
                MediaFormat f=verify.getTrackFormat(v),sound=verify.getTrackFormat(a);
                if(!"audio/mp4a-latm".equals(sound.getString(MediaFormat.KEY_MIME))||sound.getInteger(MediaFormat.KEY_SAMPLE_RATE)!=wavRate(editedWav)||sound.getInteger(MediaFormat.KEY_CHANNEL_COUNT)!=2)
                    throw new IOException("Unexpected output audio format");
                if(f.getInteger(MediaFormat.KEY_WIDTH)!=originalVideo.getInteger(MediaFormat.KEY_WIDTH)||f.getInteger(MediaFormat.KEY_HEIGHT)!=originalVideo.getInteger(MediaFormat.KEY_HEIGHT)||rotation(output)!=orientation)
                    throw new IOException("Video dimensions or rotation changed");
                verifySamples(expectedVideo,samples(verify,v,cancelled));verifySamples(expectedAudio,samples(verify,a,cancelled));
                if(Math.abs(expectedAudio.first-originalAudioFirst)>50000)throw new IOException("Audio alignment differs from the original video");
            }finally{verify.release();}
            check(cancelled);
        }finally{
            video.release();audio.release();if(mux!=null)try{mux.release();}catch(Exception ignored){}
            encoded.delete();
        }
    }
}
