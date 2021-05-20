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
package ForThePeople;

import VASSAL.build.Buildable;
import VASSAL.build.GameModule;
import VASSAL.command.CommandEncoder;
import VASSAL.configure.ColorConfigurer;
import VASSAL.i18n.Resources;
import VASSAL.preferences.Prefs;

import java.awt.Color;
import java.awt.Font;

/**
 * A tight mini-mod of the VASSAL 3.4 Chatter, to assign chat colors based on which side players are playing (rather than
 * on whether the text is from "me" or "the other player"). This would all work better if we actually passed real player
 * info with the chat commands when sending from machine to machine, but as we presently don't this will have to do!
 */
public class FTPChatter extends VASSAL.build.module.Chatter implements CommandEncoder, Buildable {

  protected static final String USA_CHAT_COLOR = "FtPAPChatColor";
  protected static final String CSA_CHAT_COLOR = "FtPCPChatColor";
  Color usaChat, csaChat;

  /**
   * Styles a chat message based on the player who sent it.
   * Overrides VASSAL's standard "my machine" / "other machine" logic with a way to assign the CP "grey" color to whoever
   * is playing CP, and the AP "blue" color to whoever is playing AP. And green to a Ref.
   */
  protected String getChatStyle(String s) {
    String style;

    if (s.startsWith(formatChat("").trim())) { //$NON-NLS-1$
      if (GameModule.getGameModule().getProperty(VASSAL.build.module.GlobalOptions.PLAYER_SIDE).equals("Confederate")) {
        style = "csachat";
      } else if (GameModule.getGameModule().getProperty(VASSAL.build.module.GlobalOptions.PLAYER_SIDE).equals("Solitaire")) {
        style = "ref";
      } else {
        style = "usachat";
      }

      if (s.contains("@csa")) {  //BR// A way to have explicit color chat messages in narrated playbacks.
        style = "csachat";
      } else if (s.contains("@usa")) {
        style = "usachat";
      } else if (s.contains("@ref")) {
        style = "ref";
      }
    } else {
      if (GameModule.getGameModule().getProperty(VASSAL.build.module.GlobalOptions.PLAYER_SIDE).equals("Confederate")) {
        style = "usachat";
      } else if (GameModule.getGameModule().getProperty(VASSAL.build.module.GlobalOptions.PLAYER_SIDE).equals("Solitaire") || GameModule.getGameModule().getProperty(VASSAL.build.module.GlobalOptions.PLAYER_SIDE).equals("<observer>")) {
        style = "ref";
      } else {
        style = "csachat";
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

    addStyle(".usachat", myFont, usaChat, "bold", 0);
    addStyle(".csachat", myFont, csaChat, "bold", 0);
    addStyle(".ref",     myFont, gameMsg2, "bold", 0);
  }

  /**
   * Add two extra color preferences, one for each player side
   */
  @Override
  public void addTo(Buildable b) {
    super.addTo(b); // Let VASSAL's chatter do its normal thing

    final Prefs globalPrefs = Prefs.getGlobalPrefs();

    final ColorConfigurer myChatColor = new ColorConfigurer(
      USA_CHAT_COLOR,
      Resources.getString("For the People - USA Chat Color"),
      new Color(9, 32, 229));

    myChatColor.addPropertyChangeListener(e -> {
      usaChat = (Color) e.getNewValue();
      makeStyleSheet(null);
    });

    globalPrefs.addOption(Resources.getString("Chatter.chat_window"), myChatColor);

    usaChat = (Color) globalPrefs.getValue(USA_CHAT_COLOR);


    final ColorConfigurer otherChatColor = new ColorConfigurer(CSA_CHAT_COLOR,
                                                                Resources.getString("For the People - CSA Chat Color"), new Color (75, 75, 75));

    otherChatColor.addPropertyChangeListener(e -> {
      csaChat = (Color) e.getNewValue();
      makeStyleSheet(null);
    });

    globalPrefs.addOption(Resources.getString("Chatter.chat_window"), otherChatColor);
    csaChat = (Color) globalPrefs.getValue(CSA_CHAT_COLOR);

    makeStyleSheet(null);
  }
}
