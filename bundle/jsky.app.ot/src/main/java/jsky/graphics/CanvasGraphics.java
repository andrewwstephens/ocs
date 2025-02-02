/*
 * Copyright 2000 Association for Universities for Research in Astronomy, Inc.,
 * Observatory Control System, Gemini Telescopes Project.
 *
 * $Id: CanvasGraphics.java 4416 2004-02-03 18:21:36Z brighton $
 */

package jsky.graphics;

import java.awt.Shape;
import java.awt.Paint;
import java.awt.geom.AffineTransform;
import java.awt.Font;
import java.awt.geom.Point2D;


/**
 * This defines an interface for drawing figures on a canvas of some type.
 *
 * @version $Revision: 4416 $
 * @author Allan Brighton
 */
public interface CanvasGraphics {

    /** Constant indicating that a figure may be selected */
    int SELECT = 1;

    /** Constant indicating that a figure may be moved */
    int MOVE = 2;

    /** Constant indicating that a figure may be resized */
    int RESIZE = 4;

    /** Constant indicating that a figure may be rotated */
    int ROTATE = 8;


    /**
     * Make and return a figure with the given shape, fill, outline and
     * line width. The shape is expected to be in screen coordinates.
     * <p>
     * The CoordinateConverter object of the image display class may be
     * used while constructing the shape to convert to screen coordinates
     * as needed.
     *
     * @see jsky.coords.CoordinateConverter
     * @see jsky.image.gui.GraphicsImageDisplay
     *
     * @param shape the shape to draw
     * @param fill the paint to use to fill the shape
     * @param outline the paint to use for the outline
     * @param lineWidth the width of the shape lines in pixels
     *
     * @return an object representing the figure
     */
    CanvasFigure makeFigure(Shape shape, Paint fill, Paint outline, float lineWidth);

    /**
     * Make and return a labeled figure with the given shape, fill, outline and
     * line width. The shape is expected to be in screen coordinates.
     *
     * @param shape the shape to draw
     * @param fill the paint to use to fill the shape
     * @param outline the paint to use for the outline
     * @param lineWidth the width of the shape lines in pixels
     * @param label the label text to be displayed with the figure
     * @param anchor SwingConstants value for the label position (CENTER, EAST, ...)
     * @param labelColor color of the label
     * @param font the label's font
     *
     * @return an object representing the figure
     */
    CanvasFigure makeLabeledFigure(Shape shape, Paint fill, Paint outline, float lineWidth,
                                          String label, int anchor, Paint labelColor, Font font);

    /**
     * Make and return a canvas label.
     *
     * @param pos the label position
     * @param text the text of the label to draw
     * @param color the paint to use to draw the text
     * @param font the font to use for the label
     */
    CanvasFigure makeLabel(Point2D.Double pos, String text, Paint color, Font font);

    /**
     * Make and return a new CanvasFigureGroup object that can be used as a
     * figure container to hold other figures.
     */
    CanvasFigureGroup makeFigureGroup();

    /**
     * Add the given figure to the canvas.
     * The figure will remain until removed again by a call to remove().
     */
    void add(CanvasFigure fig);

    /**
     * Remove the given figure from the display.
     */
    void remove(CanvasFigure fig);

    /**
     * Select the given figure.
     */
    void select(CanvasFigure fig);

    /**
     * Deselect the given figure.
     */
    void deselect(CanvasFigure fig);

    /**
     * Schedule the removal of the given figure from the display at a later time.
     * This version may be used to avoid problems with iterators working on a
     * a list of figures that should not be modified inside the loop.
     */
    void scheduleRemoval(CanvasFigure fig);

    /**
     * Return the number of figures.
     */
    int getFigureCount();

    /**
     * Transform all graphics according to the given AffineTransform object.
     */
    void transform(AffineTransform trans);

    /**
     * Set the interaction mode for the given figure to an OR'ed combination of
     * the following constants: SELECT, MOVE, RESIZE, ROTATE.
     * For example, if mode is (SELECT | MOVE | RESIZE), the figure can be selected,
     * moved, and resized. (Note that MOVE, RESIZE and ROTATE automatically assume
     * SELECT).
     */
    void setInteractionMode(CanvasFigure fig, int mode);

    /**
     * Wait for the user to drag out an area on the canvas and then
     * notify the listener with the coordinates of the box.
     */
    void selectArea(SelectedAreaListener l);

    /**
     * Schedule a repaint of the window containing the graphics.
     */
    void repaint();
}
