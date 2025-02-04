package ai.kognition.pilecv4j.gstreamer;

import static net.dempsy.utils.test.ConditionPoll.poll;

import org.freedesktop.gstreamer.Pipeline;
import org.junit.Test;

import ai.kognition.pilecv4j.gstreamer.BinManager;
import ai.kognition.pilecv4j.gstreamer.guard.GstScope;
import ai.kognition.pilecv4j.gstreamer.util.FrameCatcher;
import ai.kognition.pilecv4j.gstreamer.util.FrameEmitter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestFrameEmitterAndCatcher extends BaseTest {

   @Test
   public void testFrameEmitterToCather() throws Exception {
      try (final GstScope m = new GstScope(TestFrameEmitterAndCatcher.class);
            final FrameEmitter fe = new FrameEmitter(STREAM.toString(), 30);
            final FrameCatcher fc = new FrameCatcher("framecatcher");) {

         final Pipeline pipe = new BinManager()
               .scope(m)
               .add(fe.disown())
               .make("videoconvert")
               .caps("video/x-raw")
               .add(fc.disown())
               .buildPipeline();

         pipe.play();
         assertTrue(poll(o -> fe.isDone()));
         pipe.stop();
         assertTrue(poll(o -> !pipe.isPlaying()));
         Thread.sleep(500);
         assertEquals(30, fc.frames.size());
      }
   }
}
