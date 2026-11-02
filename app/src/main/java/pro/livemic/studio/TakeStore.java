package pro.livemic.studio;

import android.content.Context;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class TakeStore {
    public final File directory;
    private final Context context;

    public TakeStore(Context c) {
        this(c, true);
    }

    public TakeStore(Context c, boolean recoverFiles) {
        context = c.getApplicationContext();
        directory = new File(c.getFilesDir(), "takes");
        directory.mkdirs();
        if (recoverFiles) {
            recover();
        }
    }

    private void recover() {
        File[] files = directory.listFiles((d, n) -> n.endsWith(".wav"));
        if (files == null) return;
        for (File f : files) {
            String id = f.getName().replace(".wav", "");
            if (new File(directory, id + ".json").exists()
                    || f.length() <= 44
                    || (RecordingManager.active && id.equals(RecordingManager.id))) {
                continue;
            }
            try {
                int bytesPerSecond;
                try (RandomAccessFile r = new RandomAccessFile(f, "rw")) {
                    r.seek(28);
                    bytesPerSecond = Integer.reverseBytes(r.readInt());
                    if (bytesPerSecond <= 0) continue;
                    r.seek(4);
                    int riff = Integer.reverseBytes((int) f.length() - 8);
                    r.writeInt(riff);
                    r.seek(40);
                    r.writeInt(Integer.reverseBytes((int) f.length() - 44));
                }
                save(
                        id,
                        "복구된 테이크",
                        (f.length() - 44) / (double) bytesPerSecond,
                        false,
                        AppLanguage.text(context, "중단된 녹음을 복구했습니다.", "Recovered an interrupted recording.")
                );
            } catch (Exception ignored) {
            }
        }
    }

    public void save(String id, String name, double seconds, boolean video, String warning) throws Exception {
        save(id, name, seconds, video, warning, null);
    }

    public void save(String id, String name, double seconds, boolean video, String warning, JSONObject extras) throws Exception {
        JSONObject j = new JSONObject()
                .put("id", id)
                .put("name", name)
                .put("seconds", seconds)
                .put("created", System.currentTimeMillis())
                .put("video", video)
                .put("warning", warning);
        if (extras != null) {
            Iterator<String> keys = extras.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                j.put(k, extras.get(k));
            }
        }
        write(id, j);
    }

    public JSONObject read(String id) throws Exception {
        return new JSONObject(new String(Files.readAllBytes(file(id, ".json").toPath()), StandardCharsets.UTF_8));
    }

    public void update(String id, JSONObject j) throws Exception {
        write(id, j);
    }

    private void write(String id, JSONObject j) throws Exception {
        directory.mkdirs();
        File tmp = new File(directory, id + ".tmp");
        File target = new File(directory, id + ".json");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(j.toString().getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        }
        try {
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            if (target.exists()) target.delete();
            if (!tmp.renameTo(target)) {
                throw new IOException(AppLanguage.text(context, "보관함 저장 실패", "Could not save to library"), e);
            }
        }
    }

    public List<JSONObject> list() {
        List<JSONObject> list = new ArrayList<>();
        File[] fs = directory.listFiles((d, n) -> n.endsWith(".json"));
        if (fs != null) {
            for (File f : fs) {
                try {
                    list.add(new JSONObject(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8)));
                } catch (Exception ignored) {
                }
            }
        }
        list.sort((a, b) -> Long.compare(b.optLong("created"), a.optLong("created")));
        return list;
    }

    public void rename(JSONObject j, String name) throws Exception {
        j.put("name", name);
        write(j.getString("id"), j);
    }

    public void delete(String id) {
        for (String ext : new String[]{".wav", ".mid", ".mp4", ".json", ".video.mp4", ".aac.m4a"}) {
            new File(directory, id + ext).delete();
        }
    }

    public File file(String id, String ext) {
        return new File(directory, id + ext);
    }
}
