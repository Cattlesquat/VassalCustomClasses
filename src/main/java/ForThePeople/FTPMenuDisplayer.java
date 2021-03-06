//BR// (1) Clear a global property before each group-piece operation launched from here
/*
 *
 * Copyright (c) 2000-2012 by Rodney Kinney, Brent Easton
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

import VASSAL.build.Buildable;
import VASSAL.build.GameModule;
import VASSAL.build.module.Map;
import VASSAL.build.module.map.DrawPile;
import VASSAL.build.module.properties.MutableProperty;
import VASSAL.command.Command;
import VASSAL.counters.Deck;
import VASSAL.counters.EventFilter;
import VASSAL.counters.GamePiece;
import VASSAL.counters.KeyBuffer;
import VASSAL.counters.KeyCommand;
import VASSAL.counters.KeyCommandSubMenu;
import VASSAL.counters.MenuSeparator;
import VASSAL.counters.PieceFinder;
import VASSAL.counters.Properties;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.swing.Action;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.Font;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

public class FTPMenuDisplayer extends VASSAL.build.module.map.MenuDisplayer implements Buildable {
  public static Font POPUP_MENU_FONT = new Font("Dialog", 0, 10);

  protected Map map;
  protected PieceFinder targetSelector;

  @Override
  public void addTo(Buildable b) {
    targetSelector = createTargetSelector();
    map = (Map) b;
    map.addLocalMouseListener(this);
  }

  /**
   * Return a {@link PieceFinder} instance that will select a
   * {@link GamePiece} whose menu will be displayed when the
   * user clicks on the map
   * @return
   */
  protected PieceFinder createTargetSelector() {
    return new PieceFinder.PieceInStack() {
      @Override
      public Object visitDeck(Deck d) {
        Point pos = d.getPosition();
        Point p = new Point(pt.x - pos.x, pt.y - pos.y);
        if (d.getShape().contains(p)) {
          return d;
        }
        else {
          return null;
        }
      }
    };
  }

  @Override
  public void add(Buildable b) {
  }

  @Override
  public Element getBuildElement(Document doc) {
    return doc.createElement(getClass().getName());
  }

  @Override
  public void build(Element e) {
  }
  
  
  // For those who wish to override text behavior of both menus and submenus 
  protected static String getMenuText (KeyCommand keyCommand) {
    return keyCommand.getLocalizedMenuText();  
  }  
  
  // This both eliminates duplicate code AND makes this critical menu-building functionality able to "play well with others". 
  // Menu text & behavior can now be custom-classed without needing to override the monster that is MenuDisplayer#createPopup.
  protected static JMenuItem makeMenuItem(KeyCommand keyCommand, GamePiece target) {
    
    JMenuItem item = new JMenuItem(keyCommand.isMenuSeparator() ? MenuSeparator.SEPARATOR_NAME : getMenuText(keyCommand));
    item.addActionListener(keyCommand);
    if (!(target instanceof DrawPile)) {
      item.addActionListener(new FTPClearer()); //BR//
    }
    item.setFont(POPUP_MENU_FONT);
    item.setEnabled(keyCommand.isEnabled());
    
    return item;
  }

  //BR// Clears a global property flag once per stack move (to avoid duplication of certain global key commands)
  //BR// A trigger processing this can then set the flag to indicate the once-per-stack-move event has been handled.
  public static class FTPClearer implements ActionListener
  {
    public void actionPerformed(ActionEvent e) {

      Command comm = null;

      MutableProperty.Impl existingValue = (MutableProperty.Impl) GameModule.getGameModule().getMutableProperty("ClearedOncePerStackMove");
      if (existingValue != null) {
        comm = existingValue.setPropertyValue("0");
      }

      MutableProperty.Impl existingValue2 = (MutableProperty.Impl) GameModule.getGameModule().getMutableProperty("SizeOfStackMove");
      if (comm != null && existingValue2 != null) {
        //BR// An absolutely appalling way to get the number of pieces being sent the command.
        int size = 0;
        Iterator<GamePiece> i = KeyBuffer.getBuffer().getPiecesIterator();
        while (i.hasNext() ) {
          size++;
          i.next();
        }

        comm = comm.append(existingValue2.setPropertyValue(Integer.toString(size)));
      }

      if ((comm != null) && !comm.isNull()) {
        GameModule.getGameModule().sendAndLog(comm);
      }
    }
  }


  public static JPopupMenu createPopup(GamePiece target) {
    return createPopup(target, false);
  }

  /**
   *
   * @param target
   * @param global If true, then apply the KeyCommands globally,
   * i.e. to all selected pieces
   * @return
   */
  public static JPopupMenu createPopup(GamePiece target, boolean global) {
    JPopupMenu popup = new JPopupMenu();
    KeyCommand[] c = (KeyCommand[]) target.getProperty(Properties.KEY_COMMANDS);
    if (c != null) {
      ArrayList<JMenuItem> commands = new ArrayList<>();
      ArrayList<KeyStroke> strokes = new ArrayList<>();

      // Maps instances of KeyCommandSubMenu to corresponding JMenu
      HashMap<KeyCommandSubMenu, JMenu> subMenus = new HashMap<>();
      // Maps name to a list of commands with that name
      HashMap<String, ArrayList<JMenuItem>> commandNames = new HashMap<>();

      for (KeyCommand keyCommand : c) {
        keyCommand.setGlobal(global);
        KeyStroke stroke = keyCommand.getKeyStroke();
        JMenuItem item = null;
        if (keyCommand instanceof KeyCommandSubMenu) {
          JMenu subMenu = new JMenu(getMenuText(keyCommand));
          subMenu.setFont(POPUP_MENU_FONT);
          subMenus.put((KeyCommandSubMenu) keyCommand, subMenu);
          item = subMenu;
          commands.add(item);
          strokes.add(KeyStroke.getKeyStroke('\0'));
        }
        else {
          if (strokes.contains(stroke) && !keyCommand.isMenuSeparator()) {
            JMenuItem command = commands.get(strokes.indexOf(stroke));
            Action action = command.getAction();
            if (action != null) {
              String commandName =
                (String) command.getAction().getValue(Action.NAME);
              if (commandName == null ||
                      commandName.length() < keyCommand.getName().length()) {
                item = makeMenuItem(keyCommand, target);
                commands.set(strokes.indexOf(stroke), item);
              }
            }
          }
          else {
            strokes.add(((stroke != null) && !keyCommand.isMenuSeparator()) ? stroke : KeyStroke.getKeyStroke('\0'));
            item = makeMenuItem(keyCommand, target);
            commands.add(item);
          }
        }
        if (keyCommand.getName() != null &&
            keyCommand.getName().length() > 0 &&
            item != null) {
          ArrayList<JMenuItem> l = commandNames.computeIfAbsent(keyCommand.getName(), k -> new ArrayList<>());
          l.add(item);
        }
      }

      // Move commands from main menu into submenus
      for (java.util.Map.Entry<KeyCommandSubMenu, JMenu> e :
                                                        subMenus.entrySet()) {
        final KeyCommandSubMenu menuCommand = e.getKey();
        final JMenu subMenu = e.getValue();

        for (Iterator<String> it2 = menuCommand.getCommands(); it2.hasNext();) {
          final ArrayList<JMenuItem> matchingCommands =
            commandNames.get(it2.next());
          if (matchingCommands != null) {
            for (JMenuItem item : matchingCommands) {
              subMenu.add(item);
              commands.remove(item);
            }
          }
        }
      }

      // Promote contents of a single submenu [Removed as per Bug 4775]
      // if (commands.size() == 1) {
      //   if (commands.get(0) instanceof JMenu) {
      //     JMenu submenu = (JMenu) commands.get(0);
      //     commands.remove(submenu);
      //     for (int i = 0; i < submenu.getItemCount(); i++) {
      //       commands.add(submenu.getItem(i));
      //     }
      //   }
      // }

      for (JMenuItem item : commands) {
        if ((item.getText() != null) && MenuSeparator.SEPARATOR_NAME.equals(item.getText())) {
          popup.addSeparator();
        } 
        else {
          popup.add(item);
        }
      }
    }
    return popup;
  }

  @Override
  public void mousePressed(MouseEvent e) {
    maybePopup(e);
  }

  @Override
  public void mouseReleased(MouseEvent e) {
    maybePopup(e);
  }

  protected void maybePopup(MouseEvent e) {
    if (!e.isPopupTrigger()) {
      return;
    }

    final GamePiece p = map.findPiece(e.getPoint(), targetSelector);
    if (p == null) {
      return;
    }

    EventFilter filter = (EventFilter) p.getProperty(Properties.SELECT_EVENT_FILTER);
    if (filter != null && filter.rejectEvent(e)) {
      return;
    }

    JPopupMenu popup = createPopup(p, true);
    popup.addPopupMenuListener(new PopupMenuListener() {
      @Override
      public void popupMenuCanceled(PopupMenuEvent evt) {
        map.repaint();
      }

      @Override
      public void popupMenuWillBecomeInvisible(PopupMenuEvent evt) {
        map.repaint();
      }

      @Override
      public void popupMenuWillBecomeVisible(PopupMenuEvent evt) {
      }
    });

    // NB: The conversion back to component coordinates is correct. The
    // master mouse event listener on the map translates all coordinates to
    // map coordinates before passing them on.
    Point pt = map.mapToComponent(e.getPoint());

    // It is possible for the map to close before the menu is displayed
    if (map.getView().isShowing()) {

      // Inform the piece where player clicked, if it wants to know.
      KeyBuffer.getBuffer().setClickPoint(e.getPoint());      

      popup.show(map.getView(), pt.x, pt.y);
    }

    e.consume();
  }
}
