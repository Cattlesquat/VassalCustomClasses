package ca.mkiefte;

import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.Collections;
import java.util.List;

import javax.swing.JComponent;

import VASSAL.build.module.map.CounterDetailViewer;
import VASSAL.counters.GamePiece;
import VASSAL.counters.Stack;

/**
 * @author mkiefte
 *
 */
public class CardZoomer extends CounterDetailViewer {

  @Override
  protected List<GamePiece> getDisplayablePieces() {
    final List<GamePiece> pieces = super.getDisplayablePieces();
    if (pieces == null || pieces.isEmpty())
      return pieces;
    final Rectangle[] boundingBoxes = new Rectangle[pieces.size()];
    final Stack parent = pieces.get(0).getParent();
    GamePiece target = null;
    if (parent != null) {
      final Point position = parent.getPosition();
      map.getStackMetrics().getContents(parent, null, null, boundingBoxes, position.x, position.y);
      Point coord = map.componentToMap(currentMousePosition.getPoint());
      for (int i = 0; i < boundingBoxes.length; ++i) {
        if (boundingBoxes[i].contains(coord)) {
          target = parent.getPieceAt(i);
          break;
        }
      }
    }
    else
      target = pieces.get(0);
    if (target == null) return null; //BR// Prevent returning a list with a null member
    return Collections.singletonList(target);
  }

  @Override
  protected void drawGraphics(Graphics g, Point pt, JComponent comp,
                              List<GamePiece> pieces) {
    fgColor = null;
    bgColor = null;
    super.drawGraphics(g, pt, comp, pieces);
  }
}
