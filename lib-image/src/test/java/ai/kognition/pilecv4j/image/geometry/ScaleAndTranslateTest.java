package ai.kognition.pilecv4j.image.geometry;

import org.junit.Test;
import org.opencv.core.Point;

import ai.kognition.pilecv4j.image.geometry.transform.ControlPoint;
import ai.kognition.pilecv4j.image.geometry.transform.ControlPoints;
import ai.kognition.pilecv4j.image.geometry.transform.ScaleRotateAndTranslate;

import static org.junit.Assert.assertEquals;

public class ScaleAndTranslateTest {

   @Test
   public void testSimple1DXTransform() {
      final ScaleRotateAndTranslate transform = new ScaleRotateAndTranslate(new ControlPoint(new Point(1, 0), new Point(0.5, 0)),
            new ControlPoint(new Point(2, 0), new Point(10.5, 0)));

      assertEquals(0.5, transform.transform(new Point(1, 0)).x, 0.00000000001);
      assertEquals(10.5, transform.transform(new Point(2, 0)).x, 0.00000000001);
      assertEquals((10.5 + 0.5) / 2.0, transform.transform(new Point(1.5, 0)).x, 0.00000000001);
      assertEquals(0.0, transform.transform(new Point(1, 0)).y, 0.00000000001);
      assertEquals(0.0, transform.transform(new Point(2, 0)).y, 0.00000000001);
      assertEquals(0.0, transform.transform(new Point(1.5, 0)).y, 0.00000000001);
   }

   @Test
   public void testSimpleRot() {
      final double sixty = Math.toRadians(60);

      final ScaleRotateAndTranslate transform = new ScaleRotateAndTranslate(new ControlPoint(new Point(0, 0), new Point(0, 0)),
            new ControlPoint(new Point(Math.cos(sixty), Math.sin(sixty)), new Point(0, 1)));

      System.out.println(transform.transform(new Point(1, 0)));
   }

   @Test
   public void testSimple1DYTransform() {
      final ScaleRotateAndTranslate transform = new ScaleRotateAndTranslate(new ControlPoint(new Point(0, 1), new Point(0, 0.5)),
            new ControlPoint(new Point(0, 2), new Point(0, 10.5)));

      assertEquals(0.5, transform.transform(new Point(0, 1)).y, 0.00000000001);
      assertEquals(10.5, transform.transform(new Point(0, 2)).y, 0.00000000001);
      assertEquals((10.5 + 0.5) / 2.0, transform.transform(new Point(0, 1.5)).y, 0.00000000001);
   }

   @Test
   public void testSimple2DTransform() {
      final ScaleRotateAndTranslate transform = new ScaleRotateAndTranslate(new ControlPoint(new Point(1, 1), new Point(0.5, 0.5)),
            new ControlPoint(new Point(2, 2), new Point(10.5, 10.5)));

      assertEquals(0.5, transform.transform(new Point(1, 1)).x, 0.00000000001);
      assertEquals(0.5, transform.transform(new Point(1, 1)).y, 0.00000000001);
      assertEquals(10.5, transform.transform(new Point(2, 2)).x, 0.00000000001);
      assertEquals(10.5, transform.transform(new Point(2, 2)).y, 0.00000000001);
      assertEquals((10.5 + 0.5) / 2.0, transform.transform(new Point(1.5, 1.5)).x, 0.00000000001);
      assertEquals((10.5 + 0.5) / 2.0, transform.transform(new Point(1.5, 1.5)).y, 0.00000000001);
   }

   @Test
   public void testSimple2DTransformWithRotation() {
      final ScaleRotateAndTranslate transform = new ScaleRotateAndTranslate(new ControlPoint(new Point(1, 1), new Point(0, 0.5)),
            new ControlPoint(new Point(2, 2), new Point(0, 10.5)));

      assertEquals(0.0, transform.transform(new Point(1, 1)).x, 0.00000000001);
      assertEquals(0.5, transform.transform(new Point(1, 1)).y, 0.00000000001);
      assertEquals(0.0, transform.transform(new Point(2, 2)).x, 0.00000000001);
      assertEquals(10.5, transform.transform(new Point(2, 2)).y, 0.00000000001);
      assertEquals(0.0, transform.transform(new Point(1.5, 1.5)).x, 0.00000000001);
      assertEquals((10.5 + 0.5) / 2.0, transform.transform(new Point(1.5, 1.5)).y, 0.00000000001);
   }

   @Test
   public void testSimple2DTransformAlternateConstructor() {
      final ScaleRotateAndTranslate transform = new ScaleRotateAndTranslate(new ControlPoints(
            new ControlPoint[] {new ControlPoint(new Point(1, 1), new Point(0.5, 0.5)),new ControlPoint(new Point(2, 2), new Point(10.5, 10.5))}));

      assertEquals(0.5, transform.transform(new Point(1, 1)).x, 0.00000000001);
      assertEquals(0.5, transform.transform(new Point(1, 1)).y, 0.00000000001);
      assertEquals(10.5, transform.transform(new Point(2, 2)).x, 0.00000000001);
      assertEquals(10.5, transform.transform(new Point(2, 2)).y, 0.00000000001);
      assertEquals((10.5 + 0.5) / 2.0, transform.transform(new Point(1.5, 1.5)).x, 0.00000000001);
      assertEquals((10.5 + 0.5) / 2.0, transform.transform(new Point(1.5, 1.5)).y, 0.00000000001);
   }
}
