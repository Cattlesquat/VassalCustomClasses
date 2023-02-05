//BR// Overrides only one method (movePieces), to:
//BR//      (a) force everything in a stack drag to go to the same place rather than spread out.
//BR//      (b) Clear a flag at the beginning of a stack move
/*
 *
 * Copyright (c) 2000-2003 by Rodney Kinney, Jim Urbas
 * Refactoring of DragHandler Copyright 2011 Pieter Geerkens
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public
 * License (LGPL) as published by the Free Software Foundation.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Library General Public License for more details.
 *
 * You should have received a copy of the GNU Library General Public
 * License along with this library; if not, copies are available
 * at http://www.opensource.org.
 */
package ForThePeople;

import VASSAL.build.GameModule;
import VASSAL.build.module.GlobalOptions;
import VASSAL.build.module.Map;
import VASSAL.build.module.map.MovementReporter;
import VASSAL.build.module.properties.MutableProperty;
import VASSAL.command.Command;
import VASSAL.command.NullCommand;
import VASSAL.counters.BoundsTracker;
import VASSAL.counters.Deck;
import VASSAL.counters.DragBuffer;
import VASSAL.counters.GamePiece;
import VASSAL.counters.KeyBuffer;
import VASSAL.counters.PieceIterator;
import VASSAL.counters.Properties;
import VASSAL.counters.Stack;

import java.awt.Point;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * This is a MouseListener that moves pieces onto a Map window
 */
public class FTPMover extends VASSAL.build.module.map.PieceMover {
  public Command movePieces(Map map, Point p) {
    final List<GamePiece> allDraggedPieces = new ArrayList<>();
    final PieceIterator it = DragBuffer.getBuffer().getIterator();
    if (!it.hasMoreElements()) return null;

    Point     p2 = new Point(p); //BR//
    Point offset = null;
    Command comm = new NullCommand();
    final BoundsTracker tracker = new BoundsTracker();
    // Map of Point->List<GamePiece> of pieces to merge with at a given
    // location. There is potentially one piece for each Game Piece Layer.
    final HashMap<Point, List<GamePiece>> mergeTargets =
      new HashMap<>();
    while (it.hasMoreElements()) {
      dragging = it.nextPiece();
      tracker.addPiece(dragging);
      /*
       * Take a copy of the pieces in dragging.
       * If it is a stack, it is cleared by the merging process.
       */
      final ArrayList<GamePiece> draggedPieces = new ArrayList<>(0);
      if (dragging instanceof Stack) {
        draggedPieces.addAll(((Stack) dragging).asList());
      }
      else {
        draggedPieces.add(dragging);
      }

      //if (offset != null) {
      //  p = new Point(dragging.getPosition().x + offset.x,
      //    dragging.getPosition().y + offset.y);
      //}
      p = p2; //BR// Always the same point

      List<GamePiece> mergeCandidates = mergeTargets.get(p);
      GamePiece mergeWith = null;
      // Find an already-moved piece that we can merge with at the destination
      // point
      if (mergeCandidates != null) {
        for (int i = 0, n = mergeCandidates.size(); i < n; ++i) {
          final GamePiece candidate = mergeCandidates.get(i);
          if (map.getPieceCollection().canMerge(candidate, dragging)) {
            mergeWith = candidate;
            mergeCandidates.set(i, dragging);
            break;
          }
        }
      }

      // Now look for an already-existing piece at the destination point
      if (mergeWith == null) {
        mergeWith = map.findAnyPiece(p, dropTargetSelector);
        if (mergeWith == null && !Boolean.TRUE.equals(
          dragging.getProperty(Properties.IGNORE_GRID))) {
          p = map.snapTo(p);
        }

        if (offset == null) {
          offset = new Point(p.x - dragging.getPosition().x,
            p.y - dragging.getPosition().y);
        }

        if (mergeWith != null && map.getStackMetrics().isStackingEnabled()) {
          mergeCandidates = new ArrayList<>();
          mergeCandidates.add(dragging);
          mergeCandidates.add(mergeWith);
          mergeTargets.put(p, mergeCandidates);
        }
      }

      if (mergeWith == null) {
        comm = comm.append(movedPiece(dragging, p));
        comm = comm.append(map.placeAt(dragging, p));
        if (!(dragging instanceof Stack) &&
          !Boolean.TRUE.equals(dragging.getProperty(Properties.NO_STACK))) {
          final Stack parent = map.getStackMetrics().createStack(dragging);
          if (parent != null) {
            comm = comm.append(map.placeAt(parent, p));

            //BR// We've made a new stack, so put it on the list of merge targets, in case more pieces land here too
            mergeCandidates = new ArrayList<>();
            mergeCandidates.add(dragging);
            mergeCandidates.add(parent);
            mergeTargets.put(p, mergeCandidates);
          }
        }
      }
      else {
        // Do not add pieces to the Deck that are Obscured to us, or that
        // the Deck does not want to contain. Removing them from the
        // draggedPieces list will cause them to be left behind where the
        // drag started. NB. Pieces that have been dragged from a face-down
        // Deck will be be Obscued to us, but will be Obscured by the dummy
        // user Deck.NO_USER
        if (mergeWith instanceof Deck) {
          final ArrayList<GamePiece> newList = new ArrayList<>(0);
          for (GamePiece piece : draggedPieces) {
            if (((Deck) mergeWith).mayContain(piece)) {
              final boolean isObscuredToMe = Boolean.TRUE.equals(piece.getProperty(Properties.OBSCURED_TO_ME));
              if (!isObscuredToMe || (isObscuredToMe && Deck.NO_USER.equals(piece.getProperty(Properties.OBSCURED_BY)))) {
                newList.add(piece);
              }
            }
          }

          if (newList.size() != draggedPieces.size()) {
            draggedPieces.clear();
            draggedPieces.addAll(newList);
          }
        }

        // Add the remaining dragged counters to the target.
        // If mergeWith is a single piece (not a Stack), then we are merging
        // into an expanded Stack and the merge order must be reversed to
        // maintain the order of the merging pieces.
        if (mergeWith instanceof Stack) {
          for (GamePiece draggedPiece : draggedPieces) {
            comm = comm.append(movedPiece(draggedPiece, mergeWith.getPosition()));
            comm = comm.append(map.getStackMetrics().merge(mergeWith, draggedPiece));
          }
        }
        else {
          for (int i = draggedPieces.size() - 1; i >= 0; --i) {
            comm = comm.append(movedPiece(draggedPieces.get(i), mergeWith.getPosition()));
            comm = comm.append(map.getStackMetrics().merge(mergeWith, draggedPieces.get(i)));
          }
        }
      }

      for (GamePiece piece : draggedPieces) {
        KeyBuffer.getBuffer().add(piece);
      }

      // Record each individual piece moved
      allDraggedPieces.addAll(draggedPieces);

      tracker.addPiece(dragging);
    }

    if (GlobalOptions.getInstance().autoReportEnabled()) {
      final Command report = createMovementReporter(comm).getReportCommand().append(new MovementReporter.HiddenMovementReporter(comm).getReportCommand());
      report.execute();
      comm = comm.append(report);
    }

    //BR// Clears a global property flag once per stack move (to avoid duplication
    //of certain global key commands)
    //BR// A trigger processing this can then use this information to make sure a
    //particular trigger sequence is processed exactly once for the stack.
    MutableProperty.Impl existingValue = (MutableProperty.Impl) GameModule.getGameModule().getMutableProperty("ClearedOncePerStackMove");
    if (existingValue != null) {
      comm = comm.append(existingValue.setPropertyValue("0"));
    }
    MutableProperty.Impl existingValue2 = (MutableProperty.Impl) GameModule.getGameModule().getMutableProperty("SizeOfStackMove");
    if (existingValue2 != null) {
      comm = comm.append(existingValue2.setPropertyValue(Integer.toString(allDraggedPieces.size())));
    }

    // Apply key after move to each moved piece
    if (map.getMoveKey() != null) {
      comm = comm.append(applyKeyAfterMove(allDraggedPieces, map.getMoveKey()));
    }

    tracker.repaint();
    return comm;
  }
}