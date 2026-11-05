# Android TV remote controls

These are the intended YourIPTV controls; see the test report for which behavior was verified on
the onn. Streaming Device 4K Pro. A connected keyboard may send equivalent Android key events.

## Main screens

| Remote key | Action |
|---|---|
| Up / Down | Move focus through the current list or navigation rail |
| Left / Right | Move between navigation, groups, and content panes |
| OK / Select | Open the focused item or activate the focused option |
| Back | Return to the previous pane or screen; a second Back on the navigation rail exits |
| Menu / long-press OK | Open item or playback options where supported |

The TV guide is part of the TV section (there is no separate guide item in the main menu). On
the tested Google TV device, the remote's dedicated GUIDE key is intercepted by the system; use
player options → TV guide instead.

## Settings

| Remote key | Action |
|---|---|
| Up / Down | Move through categories or the selected category's settings; scroll to keep focus visible |
| OK / Select | Open the focused category or activate a setting |
| Back in a category | Return to category selection, restoring focus to the category just closed |
| Back in category selection | Exit Settings to the previous app screen |

If Settings are locked, select **Unlock Settings** on the main rail and enter the Settings
PIN before the category list opens. Category navigation does not end this temporary visit;
exiting Settings or pressing Home does. Parental restrictions still require the separate
parental PIN. The two PINs must differ.

## Live TV: menu → categories → guide

| Level | Remote key | Action |
|---|---|---|
| Main menu, on TV | OK / Right | Open the category list (the menu slides away) |
| Categories | Up / Down | Move through categories (wraps at either end); after a short pause the guide beside them shows that category without tuning a channel |
| Categories | OK / Right | Slide to that category's guide |
| Categories | Left / Back | Return to the main menu with TV focused |
| Guide | Up / Down | Move between channel rows without changing the preview |
| Guide | Right | Move the programme focus later |
| Guide | Left (short) | Earlier programme; at "now", return to the categories |
| Guide | Hold Left | Browse earlier (past) programmes |
| Guide | OK | Current programme: start the channel in the preview; select the same channel again to open full screen. Past programme with catch-up: play from the archive. Future programme: details |
| Guide | Back | If browsing other times, jump back to "now"; otherwise return to the categories with the active category focused |
| Guide | Menu / long-press OK | Programme options (reminder, recording, jump ±1 day / now, search this guide) and channel options |
| Guide | Channel / Page Up or Down | Move by a page of channels; FF / REW jump ±3 hours |
| Full screen | Back | Return to the guide on the playing channel and category, with the preview playing |

The preview continues at every level. Changing category does not change the preview. The
second OK can be pressed at any time; it is not a double-click. Locked categories show only a
locked message while browsing; OK asks for the parental PIN.

## Live full-screen playback

| Remote key | Action |
|---|---|
| Up / Down | Tune the previous / next channel in the current group |
| OK | Show playback controls |
| Left | Open the channel list |
| Right / Info | Show the channel and programme information banner |
| Menu / long-press OK | Open player options |
| Options > Audio delay | LEFT/RIGHT: ±50 ms, DOWN: reset to 0, OK: done |
| Options > Record this channel | Choose a duration; the recording appears under Recordings |
| Back | Leave playback |
| Digits | Enter a channel number; tuning occurs after the entry timeout |
| Last channel | Return to the previous channel |

## Movies and episodes

Use OK to open or play the selected item. Left / Right seek in the configured skip interval when
player controls are hidden; the default skip interval is 10 seconds. When the Media3 controls are
visible, D-pad focus moves through those controls.

Exact seeking behavior can depend on the stream format and provider. See the test report for the
current device test coverage.

## Recordings

| Remote key | Action |
|---|---|
| OK | Play a finished or in-progress recording (in progress plays what has been recorded so far); otherwise open its options |
| Menu / long-press OK | Recording: Play, Stop recording, Cancel scheduled recording, Delete recording (confirm dialog defaults to Cancel). Reminder: Watch channel, Cancel reminder |
| Reminder dialog | "Watch now" tunes the channel; "Dismiss" closes it |
