package pro.livemic.studio;

import android.content.Context;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;
import android.net.Uri;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class MediaTools {
    public static final class Decoded {
        File file;
        int rate, channels;

        Decoded(File f, int r, int c) {
            file = f;
            rate = r;
            channels = c;
        }
    }

    private static Decoded tryDecodeWav(Context c, Uri uri, File raw) {
        try (InputStream in = c.getContentResolver().openInputStream(uri)) {
            if (in == null) return null;
            byte[] header = new byte[12];
            if (readFully(in, header, 12) < 12) return null;
            ByteBuffer hb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
            if (hb.getInt() != 0x46464952) return null; // "RIFF"
            hb.getInt();
            if (hb.getInt() != 0x45564157) return null; // "WAVE"

            int audioFormat = -1, channels = 0, rate = 0, bitsPerSample = 0;
            byte[] chunkHeader = new byte[8];
            while (readFully(in, chunkHeader, 8) == 8) {
                ByteBuffer cb = ByteBuffer.wrap(chunkHeader).order(ByteOrder.LITTLE_ENDIAN);
                int chunkId = cb.getInt();
                int chunkSize = cb.getInt();
                if (chunkSize < 0) return null;
                if (chunkId == 0x20746d66) { // "fmt "
                    byte[] fmt = new byte[Math.max(16, chunkSize)];
                    if (readFully(in, fmt, chunkSize) < Math.min(16, chunkSize)) return null;
                    ByteBuffer fb = ByteBuffer.wrap(fmt).order(ByteOrder.LITTLE_ENDIAN);
                    audioFormat = Short.toUnsignedInt(fb.getShort());
                    channels = Short.toUnsignedInt(fb.getShort());
                    rate = fb.getInt();
                    fb.getInt(); // byteRate
                    fb.getShort(); // blockAlign
                    bitsPerSample = Short.toUnsignedInt(fb.getShort());
                    if ((chunkSize & 1) == 1) in.read();
                } else if (chunkId == 0x61746164) { // "data"
                    if ((audioFormat != 1 && audioFormat != 3) || channels < 1 || channels > 2 || rate <= 0) {
                        return null;
                    }
                    if (audioFormat == 1 && bitsPerSample != 16) return null;
                    if (audioFormat == 3 && bitsPerSample != 32) return null;
                    long maxBytes = (long) rate * channels * 2 * 20 * 60;
                    long remaining = chunkSize;
                    long total = 0;
                    byte[] buf = new byte[16384];
                    try (FileOutputStream out = new FileOutputStream(raw)) {
                        while (remaining > 0) {
                            int toRead = (int) Math.min(buf.length, remaining);
                            int n = in.read(buf, 0, toRead);
                            if (n <= 0) break;
                            remaining -= n;
                            if (audioFormat == 1) {
                                out.write(buf, 0, n);
                                total += n;
                            } else {
                                ByteBuffer src = ByteBuffer.wrap(buf, 0, n).order(ByteOrder.LITTLE_ENDIAN);
                                byte[] pcm16 = new byte[(n / 4) * 2];
                                ByteBuffer dst = ByteBuffer.wrap(pcm16).order(ByteOrder.LITTLE_ENDIAN);
                                while (src.remaining() >= 4) {
                                    float v = Math.max(-1f, Math.min(1f, src.getFloat()));
                                    dst.putShort((short) (v * 32767f));
                                }
                                out.write(pcm16);
                                total += pcm16.length;
                            }
                            if (total > maxBytes) {
                                throw new IOException(AppLanguage.text(c, "20분 이하의 반주 파일을 선택하세요.", "Choose a backing file no longer than 20 minutes."));
                            }
                        }
                    }
                    if (total > 0) {
                        return new Decoded(raw, rate, channels);
                    }
                    return null;
                } else {
                    long skip = chunkSize + (chunkSize & 1);
                    while (skip > 0) {
                        long s = in.skip(skip);
                        if (s <= 0) {
                            if (in.read() < 0) break;
                            skip--;
                        } else {
                            skip -= s;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static int readFully(InputStream in, byte[] b, int len) throws IOException {
        int off = 0;
        while (off < len) {
            int n = in.read(b, off, len - off);
            if (n < 0) break;
            off += n;
        }
        return off;
    }

    public static Decoded decode(Context c, Uri uri) throws Exception {
        File raw = new File(c.getCacheDir(), "backing.pcm");
        Decoded wavDecoded = tryDecodeWav(c, uri, raw);
        if (wavDecoded != null) {
            return wavDecoded;
        }
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec decoder = null;
        int rate = 48000, channels = 2, encoding = AudioFormat.ENCODING_PCM_16BIT;
        try {
            extractor.setDataSource(c, uri, null);
            int track = -1;
            MediaFormat format = null;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat f = extractor.getTrackFormat(i);
                String mime = f.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    track = i;
                    format = f;
                    break;
                }
            }
            if (track < 0 || format == null) {
                throw new IOException(AppLanguage.text(c, "오디오 트랙이 없는 파일입니다.", "This file has no audio track."));
            }
            if (format.containsKey(MediaFormat.KEY_DURATION) && format.getLong(MediaFormat.KEY_DURATION) > 20 * 60 * 1000000L) {
                throw new IOException(AppLanguage.text(c, "20분 이하의 반주 파일을 선택하세요.", "Choose a backing file no longer than 20 minutes."));
            }
            extractor.selectTrack(track);
            rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            if (channels > 2) {
                throw new IOException(AppLanguage.text(c, "모노 또는 스테레오 파일을 선택하세요.", "Choose a mono or stereo file."));
            }
            decoder = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));
            decoder.configure(format, null, null, 0);
            decoder.start();
            boolean inputDone = false, done = false;
            long total = 0;
            int idleCount = 0;
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            try (FileOutputStream out = new FileOutputStream(raw)) {
                while (!done) {
                    if (!inputDone) {
                        int i = decoder.dequeueInputBuffer(10000);
                        if (i >= 0) {
                            ByteBuffer buffer = decoder.getInputBuffer(i);
                            int n = extractor.readSampleData(buffer, 0);
                            if (n < 0) {
                                decoder.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                inputDone = true;
                            } else {
                                decoder.queueInputBuffer(i, 0, n, extractor.getSampleTime(), 0);
                                extractor.advance();
                            }
                        }
                    }
                    int i = decoder.dequeueOutputBuffer(info, 10000);
                    if (i == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        MediaFormat f = decoder.getOutputFormat();
                        rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                        channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                        if (f.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                            encoding = f.getInteger(MediaFormat.KEY_PCM_ENCODING);
                        }
                    } else if (i >= 0) {
                        idleCount = 0;
                        ByteBuffer buffer = decoder.getOutputBuffer(i);
                        if (buffer != null && info.size > 0) {
                            buffer.position(info.offset);
                            buffer.limit(info.offset + info.size);
                            buffer.order(ByteOrder.LITTLE_ENDIAN);
                            byte[] bytes;
                            if (encoding == AudioFormat.ENCODING_PCM_FLOAT) {
                                bytes = new byte[info.size / 2];
                                ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
                                while (buffer.remaining() >= 4) {
                                    b.putShort((short) (Math.max(-1f, Math.min(1f, buffer.getFloat())) * 32767f));
                                }
                            } else if (encoding == AudioFormat.ENCODING_PCM_16BIT) {
                                bytes = new byte[info.size];
                                buffer.get(bytes);
                            } else {
                                throw new IOException(AppLanguage.text(c, "지원하지 않는 PCM 형식입니다.", "Unsupported PCM format."));
                            }
                            out.write(bytes);
                            total += bytes.length;
                            if (total > (long) rate * channels * 2 * 20 * 60) {
                                throw new IOException(AppLanguage.text(c, "20분 이하의 반주 파일을 선택하세요.", "Choose a backing file no longer than 20 minutes."));
                            }
                        }
                        done = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                        decoder.releaseOutputBuffer(i, false);
                    } else if (inputDone) {
                        if (++idleCount > 100) {
                            done = true;
                        }
                    }
                }
            }
            return new Decoded(raw, rate, channels);
        } finally {
            extractor.release();
            if (decoder != null) {
                try {
                    decoder.stop();
                } catch (Exception ignored) {
                }
                decoder.release();
            }
        }
    }

    // Encode the exact processed stereo WAV to AAC, then mux it with the camera's H.264 stream.
    public static void makeVideo(File video, File wav, File target, long trimAudioUs) throws Exception {
        makeVideo(video, wav, target, trimAudioUs, 0);
    }

    public static void makeVideo(File video, File wav, File target, long trimAudioUs, long vocalDelayUs) throws Exception {
        File audio = new File(target.getParentFile(), target.getName() + ".aac.m4a");
        encodeAac(wav, audio);
        MediaExtractor v = new MediaExtractor(), a = new MediaExtractor();
        MediaMuxer muxer = null;
        try {
            v.setDataSource(video.getAbsolutePath());
            a.setDataSource(audio.getAbsolutePath());
            int vi = find(v, "video/"), ai = find(a, "audio/");
            if (vi < 0 || ai < 0) throw new IOException("영상 또는 오디오 트랙이 없습니다.");
            MediaMetadataRetriever metadata = new MediaMetadataRetriever();
            metadata.setDataSource(video.getAbsolutePath());
            String rotation = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
            metadata.release();
            muxer = new MediaMuxer(target.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            if (rotation != null) muxer.setOrientationHint(Integer.parseInt(rotation));
            int videoTrack = muxer.addTrack(v.getTrackFormat(vi)), audioTrack = muxer.addTrack(a.getTrackFormat(ai));
            muxer.start();
            copy(v, vi, muxer, videoTrack, -vocalDelayUs);
            copy(a, ai, muxer, audioTrack, -trimAudioUs);
            muxer.stop();
        } finally {
            v.release();
            a.release();
            if (muxer != null) muxer.release();
            audio.delete();
        }
        video.delete();
    }

    private static int find(MediaExtractor e, String prefix) {
        for (int i = 0; i < e.getTrackCount(); i++) {
            if (e.getTrackFormat(i).getString(MediaFormat.KEY_MIME).startsWith(prefix)) return i;
        }
        return -1;
    }

    private static void copy(MediaExtractor e, int source, MediaMuxer m, int dest, long trim) {
        e.selectTrack(source);
        ByteBuffer buffer = ByteBuffer.allocateDirect(4 * 1024 * 1024);
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        long first = e.getSampleTime();
        while (true) {
            buffer.clear();
            int n = e.readSampleData(buffer, 0);
            if (n < 0) break;
            long time = e.getSampleTime() - first - trim;
            if (time >= 0) {
                info.set(0, n, time, (e.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_SYNC) != 0 ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0);
                m.writeSampleData(dest, buffer, info);
            }
            e.advance();
        }
    }

    private static void encodeAac(File wav, File target) throws Exception {
        MediaCodec encoder = null;
        MediaMuxer muxer = null;
        try (RandomAccessFile in = new RandomAccessFile(wav, "r")) {
            in.seek(22);
            int channels = Short.toUnsignedInt(Short.reverseBytes(in.readShort()));
            int rate = Integer.reverseBytes(in.readInt());
            in.seek(44);
            MediaFormat format = MediaFormat.createAudioFormat("audio/mp4a-latm", rate, channels);
            format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            format.setInteger(MediaFormat.KEY_BIT_RATE, 192000);
            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384);
            encoder = MediaCodec.createEncoderByType("audio/mp4a-latm");
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            encoder.start();
            muxer = new MediaMuxer(target.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int track = -1;
            long samples = 0;
            boolean inputDone = false, done = false;
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            byte[] bytes = new byte[16384];
            while (!done) {
                if (!inputDone) {
                    int i = encoder.dequeueInputBuffer(10000);
                    if (i >= 0) {
                        ByteBuffer b = encoder.getInputBuffer(i);
                        b.clear();
                        int size = (Math.min(bytes.length, b.remaining()) / (channels * 2)) * (channels * 2);
                        int n = in.read(bytes, 0, size);
                        long pts = samples * 1000000L / rate;
                        if (n < 0) {
                            encoder.queueInputBuffer(i, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            b.put(bytes, 0, n);
                            encoder.queueInputBuffer(i, 0, n, pts, 0);
                            samples += n / (channels * 2);
                        }
                    }
                }
                int i = encoder.dequeueOutputBuffer(info, 10000);
                if (i == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    track = muxer.addTrack(encoder.getOutputFormat());
                    muxer.start();
                }
                if (i >= 0) {
                    ByteBuffer b = encoder.getOutputBuffer(i);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) info.size = 0;
                    if (info.size > 0 && track >= 0) {
                        b.position(info.offset);
                        b.limit(info.offset + info.size);
                        muxer.writeSampleData(track, b, info);
                    }
                    done = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    encoder.releaseOutputBuffer(i, false);
                }
            }
            muxer.stop();
        } finally {
            if (encoder != null) {
                try {
                    encoder.stop();
                } catch (Exception ignored) {
                }
                encoder.release();
            }
            if (muxer != null) muxer.release();
        }
    }
}
