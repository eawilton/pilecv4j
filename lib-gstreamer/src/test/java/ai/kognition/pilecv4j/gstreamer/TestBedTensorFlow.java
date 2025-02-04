package ai.kognition.pilecv4j.gstreamer;

import static ai.kognition.pilecv4j.gstreamer.util.GstUtils.printDetails;
import static ai.kognition.pilecv4j.tf.TensorUtils.getMatrix;
import static ai.kognition.pilecv4j.tf.TensorUtils.getScalar;
import static ai.kognition.pilecv4j.tf.TensorUtils.getVector;

import java.io.File;
import java.io.PrintStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.freedesktop.gstreamer.Gst;
import org.freedesktop.gstreamer.Pipeline;
import org.freedesktop.gstreamer.elements.URIDecodeBin;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tensorflow.Graph;
import org.tensorflow.Session;
import org.tensorflow.Session.Runner;
import org.tensorflow.Tensor;
import org.tensorflow.types.UInt8;

import ai.kognition.pilecv4j.gstreamer.BreakoutFilter.CvMatAndCaps;
import ai.kognition.pilecv4j.gstreamer.od.ObjectDetection;
import ai.kognition.pilecv4j.image.CvMat;
import ai.kognition.pilecv4j.tf.TensorUtils;
import ai.kognition.pilecv4j.image.Utils;

public class TestBedTensorFlow extends BaseTest {
   private static final Logger LOGGER = LoggerFactory.getLogger(TestBedTensorFlow.class);

   public static final double threshold = 0.5;
   public static final int fontHeight = 20;
   public static final double fontScale = 3;
   public static final String rtmpDestinationUrl = "rtmp://localhost:1935/live/";

   public static final String defaultSsdModel = "tensor/ssd_mobilenet_v1_coco_2017_11_17/frozen_inference_graph.pb";
   final static URI labelUri = new File(
         TestBedTensorFlow.class.getClassLoader().getResource("tensor/ssd_mobilenet_v1_coco_2017_11_17/mscoco_label_map.txt").getFile())
               .toURI();

   public static void main(final String[] args) throws Exception {

      final URI modelUri = new File(
            TestBedTensorFlow.class.getClassLoader().getResource(defaultSsdModel)
                  .getFile()).toURI();

      // TODO: There is a protobufs problem mixing OpenCv and TensorFlow. TensorFlow uses
      // a much later version than the one COMPILED INTO (WTF?) opencv so we need the TensorFlow
      // library loaded first.
      final Graph graph = TensorUtils.inflate(Files.readAllBytes(Paths.get(modelUri)));

      // setGstLogLevel(Level.FINE);

      Gst.init(TestBedTensorFlow.class.getSimpleName(), args);

      // ====================================================================
      final List<String> labels = Files.readAllLines(Paths.get(labelUri), Charset.forName("UTF-8"));
      try (final Session session = new Session(graph);) {

         final BreakoutFilter bin = new BreakoutFilter("od")
               .connectSlowFilter((final CvMatAndCaps bac) -> {
                  final CvMat mat = bac.mat;
                  final List<ObjectDetection> det = mat.rasterOp(r -> {
                     final ByteBuffer bb = r.underlying();
                     bb.rewind();
                     final int w = bac.width;
                     final int h = bac.height;

                     try (final Tensor<UInt8> tensor = Tensor.create(UInt8.class, new long[] {1,h,w,3}, bb);) {
                        bb.rewind(); // need to rewind after being passed to create
                        return executeGraph(graph, tensor, session);
                     }
                  });

                  final int thickness = (int)(0.003d * mat.width());
                  final int fontShift = thickness + (int)(fontHeight * fontScale);
                  det.stream().filter(d -> d.probability > threshold)
                        .forEach(d -> {
                           final int classification = d.classification;
                           final String label = classification < labels.size() ? labels.get(classification)
                                 : Integer.toString(classification);
                           Imgproc.putText(mat, label,
                                 new Point(d.xmin * mat.width() + thickness, d.ymin * mat.height() + fontShift),
                                 Utils.OCV_FONT_HERSHEY_SIMPLEX, fontScale, new Scalar(0xff, 0xff, 0xff), thickness);
                           Imgproc.rectangle(mat, new Point(d.xmin * mat.width(), d.ymin * mat.height()),
                                 new Point(d.xmax * mat.width(), d.ymax * mat.height()),
                                 new Scalar(0xff, 0xff, 0xff), thickness);
                        });
               });

         LOGGER.trace("Returning object detected Buffer");
         // return FlowReturn.OK;

         final BinManager bb = new BinManager()
               // .delayed(new URIDecodeBin("source")).with("uri", BaseTest.STREAM.toString())
               // .delayed(new URIDecodeBin("source")).with("uri", "file:///mnt/media-nas/Movies/Dave Smith Libertas
               // (2017).mp4")
               .delayed(new URIDecodeBin("source")).with("uri", "rtsp://admin:greg0rmendel@10.1.1.20:554/")
               // .with("uri", "rtsp://admin:greg0rmendel@10.1.1.20:554/")
               // .make("v4l2src")
               .make("videoconvert")
               // .caps("video/x-raw,width=640,height=480")
               .caps("video/x-raw,format=RGB")
               // .caps("video/x-raw")
               // .teeWithMultiqueue(
               // new Branch("b2_")
               // .make("queue")
               // .make("xvimagesink"),
               // new Branch("b1_")
               // .make("queue")
               .add(bin)
               .make("videoconvert")
               // .make("x264enc").with("key-int-max", 217)
               // .make("flvmux").with("streamable", true)
               // .make("rtmpsink").with("location", rtmpDestinationUrl + "od")
               // .make("videoconvert")
               .make("xvimagesink") // .with("force-aspect-ratio", "true")
         // .make("fakesink")

         ;
         // );

         final Pipeline pipe = bb
               // This code actually works to switch the dev for v4l2src
               // .onError((Pipeline p, GstObject source, int code, String message) -> {
               // System.out.println("Source:" + source);
               // System.out.println("Code:" + code);
               // System.out.println("Message:" + message);
               // source.set("device", "/dev/video1");
               // p.play();
               // })
               .buildPipeline();

         // instrument(pipe);
         pipe.play();

         Thread.sleep(5000);

         try (final PrintStream ps = new PrintStream(new File("/tmp/pipeline.txt"))) {
            printDetails(pipe, ps);
         }

         Gst.main();
      }
   }

   @SuppressWarnings("unchecked")
   private static List<ObjectDetection> executeGraph(final Graph graph, final Tensor<UInt8> imageTensor, final Session session) {
      // for (final Operation op : (Iterable<Operation>) (() -> graph.operations())) {
      // System.out.println(op.name());
      // }
      final Runner runner = session.runner();
      final List<Tensor<?>> result = runner
            .feed("image_tensor:0", imageTensor)
            .fetch("num_detections:0")
            .fetch("detection_classes:0")
            .fetch("detection_scores:0")
            .fetch("detection_boxes:0")
            .run();

      // System.out.println(result);
      // for (int i = 0; i < result.size(); i++) {
      // final Tensor<Float> tensor = (Tensor<Float>) result.get(i);
      // final Object res = toNativeArray(tensor, float.class);
      // if (tensor.shape().length > 1)
      // System.out.println(Arrays.deepToString((Object[]) res));
      // else
      // System.out.println(Arrays.toString((float[]) res));
      // }

      if(result.size() != 4)
         throw new IllegalStateException("Expected 4 tensors from object detection. Received " + result.size());

      // first tensor is num
      final int numdetec = (int)getScalar((Tensor<Float>)result.get(0));

      // this is the classification results
      final Tensor<Float> classificationTensor = (Tensor<Float>)result.get(1);

      // This tensor should be 1 x numdetec.
      final long[] shape = classificationTensor.shape();
      if(shape.length != 2 || shape[0] != 1 || shape[1] != numdetec)
         throw new IllegalStateException("Expected the classification Tensor to be of dimentions (1 x " + numdetec + ") but appears to be "
               + Arrays.toString(shape));

      final float[] tmpf = getVector(classificationTensor);
      final int[] classifications = IntStream.range(0, tmpf.length)
            .map(i -> (int)tmpf[i])
            .toArray();

      // System.out.println("Classifications: " + Arrays.toString(classifications));

      final float[] probabilities = getVector((Tensor<Float>)result.get(2));

      // System.out.println("Probabilities: " + Arrays.toString(probabilities));

      final float[][] boxes = getMatrix((Tensor<Float>)result.get(3));

      // System.out.println("Bounding boxes: " + Arrays.deepToString(boxes));

      final List<ObjectDetection> detections = IntStream.range(0, numdetec)
            .mapToObj(i -> {
               final float[] box = boxes[i];
               return new ObjectDetection(probabilities[i], classifications[i], box[0], box[1], box[2], box[3]);
            })
            .collect(Collectors.toList());

      // System.out.println("Detections:");
      // detections.stream().forEach(d -> System.out.println(d));
      return detections;
   }
}
