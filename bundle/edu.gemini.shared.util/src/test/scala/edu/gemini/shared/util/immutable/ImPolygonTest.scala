package edu.gemini.shared.util.immutable

import java.awt.geom.{Rectangle2D, Point2D, AffineTransform, PathIterator}
import org.junit.Test
import org.junit.Assert._

class ImPolygonTest {
  private val epsilon = 1e-3
  private val transforms = scala.None :: List(AffineTransform.getTranslateInstance(100.0, 200.0),
                                              AffineTransform.getTranslateInstance(-50.0, -100.0),
                                              AffineTransform.getRotateInstance(math.Pi/4),
                                              AffineTransform.getRotateInstance(-math.Pi/2),
                                              AffineTransform.getRotateInstance(math.Pi)).map(scala.Option(_))

  /**
   * Test a set of 0 <= n <= 5 collinear points, i.e. a line segment, which has area 0 and, as such, no interior and is
   * considered empty. This also tests the trivial "empty polygon" and the "point-based polygon" cases.
   */
  @Test def testCollinearPoints(): Unit = {
    Range(0,6).foreach(runCollinearTest(_))
  }

  /**
   * Run tests for regular n-gons for 3 <= n <= 8 (triangle through to octagon).
   */
  @Test def testRegularNgons(): Unit = {
    Range(3,9).foreach(runRegularNgonTest(_))
  }

  /**
   * Create a bizarre flat-bottomed V shaped polygon with lots of empty space in the boundary, and test
   * "facial features" that overlap all categories of inner / outer / containment / noncontainment / intersection /
   * empty intersection.
   */
  @Test def testV(): Unit = {
    val points      = List((-4.0,  4.0),  (-2.0, 0.0),  (2.0, 0.0), (4.0, 4.0), (0.0, 2.0))
    val innerPoints = List((-3.75, 3.75), (3.75, 3.75), (0.0, 1.75))
    val outerPoints = List((-3.0,  1.0),  (3.0,  1.0),  (0.0, 3.0))

    // Areas in the bounds and intersecting the polygon, but not contained in it.
    val leftEye     = (-3.5, 2.0, 1.5, 1.5)
    val rightEye    = ( 2.0, 2.0, 1.5, 1.5)

    // Areas contained in the polygon.
    val leftPupil   = (-3.0, 2.5, 0.5, 0.5)
    val rightPupil  = ( 2.5, 2.5, 0.5, 0.5)
    val mouth       = (-2.0, 0.5, 4.0, 1.0)

    // An area in the bounds but completely out of the polygon.
    val halo        = (-1.0, 3.0, 2.0, 0.5)

    val innerAreas        = List(leftPupil, rightPupil, mouth)
    val outerAreas        = List(leftEye, rightEye, halo)
    val intersectAreas    = List(leftEye, leftPupil, rightEye, rightPupil, mouth)
    val nonintersectAreas = List(halo)
    runPolygonTests(points, innerPoints, outerPoints, innerAreas, outerAreas, intersectAreas, nonintersectAreas)
  }

  /**
   * Create a line segment represented by n collinear points starting at init and with the specified direction
   * vector. Note that this should be empty since it has area 0.
   * The resultant line segment has start point init and end point init + (n-1)vector.
   * @param n      number of points specifically represented in the polygon definition on the line segment
   * @param init   the start point of the line segment
   * @param vector the direction vector of the line segment
   */
  def runCollinearTest(n: Int, init: (Double, Double) = (10.0, 15.0), vector: (Double, Double) = (1.0, 2.0)): Unit = {
    val points            = Range(0,n).map{s => (init._1 + s * vector._1, init._2 + s * vector._2)}.toList
    def outerAreas        = points.map(x => (x._1, x._2, 0.0, 0.0))
    runPolygonTests(points, outerPoints = points, outerAreas = outerAreas)
  }

  /**
   * Create a regular n-gon of the specified radius, centered at the origin.
   * @param n the number of vertices in the regular polygon
   * @param radius the radius of the smallest circle in which the polygon can be inscribed
   */
  private def runRegularNgonTest(n: Int, radius: Double = 1.0): Unit = {
    assertTrue(n >= 3)

    // Create the points of the regular polygon.
    val points = Range(0, n).map(i => (radius * math.cos(2 * i * math.Pi / n),
                                       radius * math.sin(2 * i * math.Pi / n))).toList

    // Define the inner points as the center and all vectors to the vertices - a multiple of epsilon to move them
    // closer to the inside.
    val innerPoints = (0.0,0.0) :: points.map{ case (x,y) => (x * (1 - epsilon), y * (1 - epsilon)) }

    // The outer points are vectors extended by a multiple of epsilon to move them closer to the outside.
    val outerPoints = points.map{ case (x,y) => (x * (1 + epsilon), y * (1 + epsilon)) }

    // Create the largest inner area: calculate the distance from the center point to any midpoint, which should
    // be the same for all line segments comprising the polygon.
    val midPointDist = {
      val (x0,y0)      = points(0)
      val (x1,y1)      = points(1)
      val (xmid, ymid) = ((x0+x1)/2, (y0+y1)/2)
      math.sqrt(xmid*xmid + ymid*ymid) - epsilon
    }

    // Get the coordinates of the point (z,z) at distance midPointDist from the origin. This is the upper right
    // vertex of the rectangle.
    val z = midPointDist / math.sqrt(2)
    val innerAreas = List((-z, -z, 2*z, 2*z))
    val outerAreas = List((-radius, -radius, 2*radius, 2*radius))

    val intersectAreas    = innerAreas ++ outerAreas
    val nonintersectAreas = List((-4*radius, -3*radius, radius, radius))
    runPolygonTests(points, innerPoints, outerPoints, innerAreas, outerAreas, intersectAreas, nonintersectAreas)
  }

  /**
   * Run test cases for the polygon described by points, both in terms of creating the ImPolygon with all points
   * at once, and also in terms of creating the ImPolygon via addPoint, adding one point at a time.
   * The tests are executed for every translation in the translations member.
   * @note  Points are of the form (x,y) and areas are rectangular of the form (x,y,w,h).
   * @param points             the points describing the polygon
   * @param innerPoints        points to test as belonging to the interior of the polygon
   * @param outerPoints        points to test as being exterior to the polygon
   * @param innerAreas         areas to test as being contained within the polygon
   * @param outerAreas         areas to test as not being contained within the polygon (may intersect)
   * @param intersectAreas     areas to test as intersecting the polygon
   * @param nonintersectAreas  areas to test as not intersecting the polygon
   */
  private def runPolygonTests(points:            List[(Double, Double)],
                              innerPoints:       List[(Double, Double)] = Nil,
                              outerPoints:       List[(Double, Double)] = Nil,
                              innerAreas:        List[(Double, Double, Double, Double)] = Nil,
                              outerAreas:        List[(Double, Double, Double, Double)] = Nil,
                              intersectAreas:    List[(Double, Double, Double, Double)] = Nil,
                              nonintersectAreas: List[(Double, Double, Double, Double)] = Nil): Unit = {
    val polyAllAtOnce = ImPolygon(points)
    transforms.foreach { at =>
      runPolygonTest(polyAllAtOnce, points, innerPoints, outerPoints, innerAreas, outerAreas,
                     intersectAreas, nonintersectAreas, at)
    }

    val polyAddPoints = points.foldLeft(ImPolygon())((poly, pt) => poly.addPoint(pt._1, pt._2))
    transforms.foreach { at =>
      runPolygonTest(polyAddPoints, points, innerPoints, outerPoints, innerAreas, outerAreas,
                     intersectAreas, nonintersectAreas, at)
    }
  }

  /**
   * Run all tests for a given polygon and AffineTransform.
   * Unspecified parameters are the same as in runPolygonTests.
   * @see   runPolygonTests
   * @param poly the polygon to test
   * @param at   the affine transformation to execute, or None if no transformation is to be used
   */
  private def runPolygonTest(poly:              ImPolygon,
                             points:            List[(Double, Double)],
                             innerPoints:       List[(Double, Double)],
                             outerPoints:       List[(Double, Double)],
                             innerAreas:        List[(Double, Double, Double, Double)],
                             outerAreas:        List[(Double, Double, Double, Double)],
                             intersectAreas:    List[(Double, Double, Double, Double)],
                             nonintersectAreas: List[(Double, Double, Double, Double)],
                             at:                scala.Option[AffineTransform]): Unit = {
    // Confirm that the polygon was created as expected using a PathIterator with a possible transformation.
    def confirmVertices(): Unit = {
      // Transform a point using at.
      def transform(p: (Double, Double)): (Double, Double) = {
        at.map { t =>
          val p1 = new Point2D.Double(p._1, p._2)
          val p2 = new Point2D.Double()
          t.transform(p1, p2)
          (p2.getX, p2.getY)
        }.getOrElse(p)
      }

      val iter = poly.getPathIterator(at.orNull)

      def point: (Int, (Double, Double)) = {
        val coords   = Array.fill(6)(0.0)
        val pathType = iter.currentSegment(coords)
        assertTrue(Set(PathIterator.SEG_MOVETO, PathIterator.SEG_LINETO, PathIterator.SEG_CLOSE).contains(pathType))
        (pathType, (coords(0), coords(1)))
      }

      // Initial starting point.
      assertFalse(iter.isDone)
      val (initX, initY) = points match {
        case Nil           => (0.0, 0.0)
        case (x0, y0) :: _ => (x0,  y0)
      }
      assertEquals(point, (PathIterator.SEG_MOVETO, transform((initX, initY))))
      iter.next()

      // Each intermediate point.
      points.drop(1).foreach {
        case (x, y) =>
          assertFalse(iter.isDone)
          assertEquals(point, (PathIterator.SEG_LINETO, transform((x, y))))
          iter.next()
      }

      // The path must be closed.
      assertFalse(iter.isDone)
      assertEquals(point, (PathIterator.SEG_CLOSE, (0.0, 0.0)))
      iter.next()

      assertTrue(iter.isDone)
    }

    val ((minx, width), (miny, height)) = {
      def minPointMaxDist(vals: List[Double]): (Double, Double) = {
        val (maxval, minval) = {
          if (vals.isEmpty) (0.0,      0.0)
          else              (vals.max, vals.min)
        }
        (minval, maxval - minval)
      }
      val (xvals, yvals) = points.unzip
      (minPointMaxDist(xvals), minPointMaxDist(yvals))
    }

    val bounds2D = poly.getBounds2D
    assertEquals(bounds2D.getX,      minx,   epsilon)
    assertEquals(bounds2D.getY,      miny,   epsilon)
    assertEquals(bounds2D.getWidth,  width,  epsilon)
    assertEquals(bounds2D.getHeight, height, epsilon)

    confirmVertices()

    // Now check the inner and outer points.
    assertTrue (innerPoints.forall(p => poly.contains(p._1, p._2)))
    assertFalse(outerPoints.exists(p => poly.contains(p._1, p._2)))

    // And the areas.
    assertTrue (innerAreas.forall(a => poly.contains(a._1, a._2, a._3, a._4)))
    assertFalse(outerAreas.exists(a => poly.contains(a._1, a._2, a._3, a._4)))

    // And the intersections.
    assertTrue (intersectAreas.forall   (a => poly.intersects(a._1, a._2, a._3, a._4)))
    assertFalse(nonintersectAreas.exists(a => poly.intersects(a._1, a._2, a._3, a._4)))
  }
}
