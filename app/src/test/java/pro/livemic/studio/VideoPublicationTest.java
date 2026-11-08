package pro.livemic.studio;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class VideoPublicationTest {
    @Test public void unfinishedMuxIsNotPublishedAndFailedPublicationPreservesExistingVideo() throws Exception {
        File directory=RuntimeEnvironment.getApplication().getCacheDir();
        File target=new File(directory,"publication.mp4"),pending=new File(directory,"publication.mp4.pending.mp4");
        target.delete();Files.write(pending.toPath(),new byte[]{4,5,6});
        assertFalse("Writing mux output does not expose a final video",target.exists());
        MediaTools.publishVideo(pending,target);
        assertArrayEquals(new byte[]{4,5,6},Files.readAllBytes(target.toPath()));assertFalse(pending.exists());
        Files.write(pending.toPath(),new byte[0]);
        try{MediaTools.publishVideo(pending,target);fail("Empty output cannot replace a video");}catch(IOException expected){}
        assertArrayEquals(new byte[]{4,5,6},Files.readAllBytes(target.toPath()));
    }
}
