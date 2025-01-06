/*
 *
 * Copyright (c) 2000-2020 by Rodney Kinney, Brian Reynolds, VASSAL
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
package PathsOfGlory;

import VASSAL.build.Buildable;
import VASSAL.build.GameModule;
import VASSAL.build.module.GlobalOptions;
import VASSAL.command.CommandEncoder;
import VASSAL.configure.ColorConfigurer;
import VASSAL.i18n.Resources;
import VASSAL.preferences.Prefs;
import VASSAL.tools.ErrorDialog;
import VASSAL.tools.QuickColors;

import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import java.awt.Color;
import java.awt.Font;
import java.io.IOException;

/**
 * A tight mini-mod of the VASSAL 3.4 Chatter, to assign chat colors based on which side players are playing (rather than
 * on whether the text is from "me" or "the other player"). This would all work better if we actually passed real player
 * info with the chat commands when sending from machine to machine, but as we presently don't this will have to do!
 */
public class POGChatter extends VASSAL.build.module.Chatter implements CommandEncoder, Buildable {

  protected static final String AP_CHAT_COLOR = "PoGAPChatColor";
  protected static final String CP_CHAT_COLOR = "PoGCPChatColor";
  Color apChat, cpChat;

  /**
   * Styles a chat message based on the player who sent it.
   * Overrides VASSAL's standard "my machine" / "other machine" logic with a way to assign the CP "grey" color to whoever
   * is playing CP, and the AP "blue" color to whoever is playing AP. And green to a Ref.
   */
  protected String getChatStyle(String s) {
    String style;

    if (s.startsWith(formatChat("").trim())) { //$NON-NLS-1$
      if (GameModule.getGameModule().getProperty(VASSAL.build.module.GlobalOptions.PLAYER_SIDE).equals("Central Powers")) {
        style = "cpchat";
      } else if (GameModule.getGameModule().getProperty(VASSAL.build.module.GlobalOptions.PLAYER_SIDE).equals("Solitaire")) {
        style = "ref";
      } else {
        style = "apchat";
      }

      final String ss = s.toLowerCase();
      if (ss.contains("@cp")) {  //BR// A way to have explicit color chat messages in narrated playbacks.
        style = "cpchat";
      } else if (ss.contains("@ap")) {
        style = "apchat";
      } else if (ss.contains("@ref") || ss.contains("@@")) {
        style = "ref";
      }
    } else {
      if (GameModule.getGameModule().getProperty(VASSAL.build.module.GlobalOptions.PLAYER_SIDE).equals("Central Powers")) {
        style = "apchat";
      } else if (GameModule.getGameModule().getProperty(VASSAL.build.module.GlobalOptions.PLAYER_SIDE).equals("Solitaire") || GameModule.getGameModule().getProperty(VASSAL.build.module.GlobalOptions.PLAYER_SIDE).equals("<observer>")) {
        style = "ref";
      } else {
        style = "cpchat";
      }
    }

    return style;
  }

  /**
   * Adds our two player color styles to the HTML stylesheet
   */
  protected void makeStyleSheet(Font f) {
    super.makeStyleSheet(f); // Let VASSAL's chatter build its normal stylesheet

    if (style == null) {
      return;
    }

    addStyle(".apchat", myFont, apChat, "bold", 0);
    addStyle(".cpchat", myFont, cpChat, "bold", 0);
    addStyle(".ref",    myFont, gameMsg2, "bold", 0);
  }


  /**
   * Display a message in the text area. Ensures we execute in the EDT
   */
  public void show(String s) {
    if (SwingUtilities.isEventDispatchThread()) {
      myDoShow(s);
    }
    else {
      SwingUtilities.invokeLater(() -> myDoShow(s));
    }
  }

  /**
   * Remove any "<<Player Name>> - " header from a marked string
   * @param s string that might have header
   * @return string without header
   */
  protected String removeHeader(String s) {
    final int index = s.indexOf(" - ");
    if (index < 0) return s;
    return s.substring(index + 3);
  }

  /**
   * Replaces doShow() from Chatter -- because I foolishly made it private instead of protected :)
   * @param s chat string from command
   */
  public void myDoShow(String s) {
    final String style;
    final boolean html_allowed;

    // Choose an appropriate style to display this message in
    s = s.trim();
    if (!s.isEmpty()) {
      if (s.startsWith("*")) {
        html_allowed = (QuickColors.getQuickColor(s, "*") >= 0) || GlobalOptions.getInstance().chatterHTMLSupport();
        style = QuickColors.getQuickColorHTMLStyle(s, "*");
        s = QuickColors.stripQuickColorTag(s, "*");
      }
      else if (s.startsWith("-")) {
        html_allowed = true;
        style = (QuickColors.getQuickColor(s, "-") >= 0) ? QuickColors.getQuickColorHTMLStyle(s, "-") : "sys"; //NON-NLS
        s = QuickColors.stripQuickColorTag(s, "-");
      }
      else {
        style = getChatStyle(s);

        if (s.contains("@cp")) {
          s = removeHeader(s).replace("@cp", "");
          if (s.startsWith("@@")) {
            s = s.substring(2);
          }
          else {
            s = "CP Player: " + s;
          }
        }
        else if (s.contains("@ap")) {
          s = removeHeader(s).replace("@ap", "");
          if (s.startsWith("@@")) {
            s = s.substring(2);
          }
          else {
            s = "AP Player: " + s;
          }
        }
        else if (s.contains("@ref")) {
          s = removeHeader(s).replace("@ref", "");
          if (s.startsWith("@@")) {
            s = s.substring(2);
          }
          else {
            s = "Moderator: " + s;
          }
        }
        else if (s.contains("@@")) {
          s = removeHeader(s).replace("@@", "");
        }

        boolean html = false;

        // Moderator can mark die rolls with @cdX and @adX where X is 1-6
        for (int x = 1; x <= 6; x++) {
          final String cpstring = "@cd" + x;
          if (s.contains(cpstring)) {
            s = s.replace(cpstring, "<img src=\"d6-" + x + "-grey.png\" width=\"14\" height=\"14\">");
            html = true;
          }
          final String apstring = "@ad" + x;
          if (s.contains(apstring)) {
            s = s.replace(apstring, "<img src=\"d6-" + x + "-blue.png\" width=\"14\" height=\"14\">");
            html = true;
          }
        }

        html_allowed = html;
      }
    }
    else {
      style = "msg";  //NON-NLS
      html_allowed = false;
    }

    // Disable unwanted HTML tags in contexts where it shouldn't be allowed:
    // (1) Anything received from chat channel, for security reasons
    // (2) Legacy module "report" text when not explicitly opted in w/ first character or preference setting
    if (!html_allowed) {
      s = s.replaceAll("<", "&lt;")  //NON-NLS // This prevents any unwanted tag from functioning
        .replaceAll(">", "&gt;"); //NON-NLS // This makes sure > doesn't break any of our legit <div> tags
    }

    // Now we have to fix up any legacy angle brackets around the word <observer>
    final String keystring = Resources.getString("PlayerRoster.observer");
    final String replace = keystring.replace("<", "&lt;").replace(">", "&gt;"); //NON-NLS
    if (!replace.equals(keystring)) {
      s = s.replace(keystring, replace);
    }

    // Insert a div of the correct style for our line of text. Module designer
    // still free to insert <span> tags and <img> tags and the like in Report
    // messages.
    try {
      kit.insertHTML(doc, doc.getLength(), "\n<div class=\"" + style + "\">" + s + "</div>", 0, 0, null); //NON-NLS
    }
    catch (BadLocationException | IOException ble) {
      ErrorDialog.bug(ble);
    }

    conversationPane.repaint();
  }


  /**
   * Add two extra color preferences, one for each player side
   */
  @Override
  public void addTo(Buildable b) {
    super.addTo(b); // Let VASSAL's chatter do its normal thing

    GameModule mod = (GameModule) b;
    final Prefs globalPrefs = Prefs.getGlobalPrefs();

    final ColorConfigurer myChatColor = new ColorConfigurer(
      AP_CHAT_COLOR,
      Resources.getString("Paths of Glory - AP Chat Color"),
      new Color(9, 32, 229));

    myChatColor.addPropertyChangeListener(e -> {
      apChat = (Color) e.getNewValue();
      makeStyleSheet(null);
    });

    globalPrefs.addOption(Resources.getString("Chatter.chat_window"), myChatColor);

    apChat = (Color) globalPrefs.getValue(AP_CHAT_COLOR);


    final ColorConfigurer otherChatColor = new ColorConfigurer(CP_CHAT_COLOR,
                                                                Resources.getString("Paths of Glory - CP Chat Color"), new Color (75, 75, 75));

    otherChatColor.addPropertyChangeListener(e -> {
      cpChat = (Color) e.getNewValue();
      makeStyleSheet(null);
    });

    globalPrefs.addOption(Resources.getString("Chatter.chat_window"), otherChatColor);
    cpChat = (Color) globalPrefs.getValue(CP_CHAT_COLOR);

    makeStyleSheet(null);
  }
}
