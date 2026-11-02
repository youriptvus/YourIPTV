# Release notes

## 0.3.3 (version code 7)

- Fix: choppy, pixelated and skipping live playback on phones such as the moto g 2025. The cause
  was splitting H.264 MPEG-TS streams into access units. With the old setting, each delimiter
  (AUD), SEI, SPS and PPS unit was sent to the decoder as its own sample, so each picture arrived
  as about 4 decoder inputs. The phone's decoder could not keep up at 60 fps. It fell behind,
  dropped late frames and jumped to the next keyframe about every 4 seconds.
  - YourIPTV now checks each H.264 stream at the start. Streams with access-unit delimiters are
    split at the delimiters; streams without them still use access-unit detection.
  - Each picture is now one decoder input. Fullscreen playback on the phone is a steady 60 fps
    (was about 7 fps), whether the system bars are shown or hidden. Android TV playback is
    unchanged.
## 0.3.2 (version code 6)

- Fix: opening or browsing a TV category (including All channels) now starts its guide at the first
  channel in the provider's playlist order, as TiviMate does. Before, it jumped to the playing
  channel, so channels above it (for example BIG BROTHER at 1) were scrolled out of view and the
  list looked out of order. Back from full screen still returns to the playing channel.
## 0.3.1 (version code 5)

- Guide sizing and density now match TiviMate's measured proportions on a 1080p TV (measured at
  the same resolution and display settings), without a blanket scale transform:
  - channel rows 56 dp -> 40 dp, about 8 rows visible instead of about 4;
  - channel names, programme titles and time labels 16 sp -> 14 sp (channel names medium weight);
  - channel column 180 dp -> 260 dp with channel logos (44 dp slot) and a 40 dp number column;
  - timeline header 32 dp -> 28 dp; 2 hours of programmes visible instead of 2.5;
  - the "TV · category" title and item count no longer sit above the preview in the categories
    and guide; they appear as one small line at the top of the information panel instead;
  - the preview stays at a fixed 320 dp wide, as in TiviMate.
- Opening the categories slides the guide right at its full size, partly off screen, like
  TiviMate, instead of squeezing it. In narrow layouts the time labels show every hour, so they no
  longer run into each other.
- No behaviour changes: categories, preview, full screen, focus and Back work as in 0.3.0.
## 0.3.0 (version code 4)

- Live TV and the TV guide are one flow, modelled on TiviMate: Main menu → TV → categories →
  guide → preview → full screen. The separate "TV guide" entry has been removed from the main
  menu, and the standalone guide screen has been folded into Live TV. EPG data, EPG settings,
  catch-up, reminders, recording and programme options are unchanged.
- Selecting All channels or a category (OK or RIGHT) slides to that category's guide. Moving
  through the categories re-filters the guide beside them without tuning a channel. Locked
  categories show only a locked message until OK and the parental PIN.
- Back steps one level at a time: full screen → guide (same channel and category) → categories
  (active category focused) → main menu (TV focused). In the guide, Back first returns to "now"
  if you have browsed to other times. A short LEFT at "now" also returns to the categories.
- Hold LEFT in the guide to browse earlier (past) programmes; RIGHT moves later. The category
  list wraps around at either end.
- The preview keeps playing through every level. The first OK previews, and selecting the same
  channel again opens full screen. Player options → TV guide returns to the playing channel in
  its category.
- Movies, Shows, Settings, parental controls and the separate Settings PIN are unchanged.

## 0.2.0 (version code 3)

- Live TV now presents categories beside a scrollable channel and programme grid.
- Live channels can be previewed in both Live TV and the separate TV Guide. A second selection
  opens full screen, with no double-click or automatic full-screen timeout.
- Live playback is transferred between preview and full screen, keeping the listing visible and
  restoring it on Back. Parental controls remain in effect.
- Ended or unavailable live streams provide reconnection or retry feedback without trapping
  navigation. A preview that buffers continuously for 20 seconds shows a slow-loading hint while
  continuing to try.
- Programme details, catch-up, reminders, favorites, recordings, and the separate Guide remain
  available. Movies and series retain their existing browsing and playback paths.
- Starting or scheduling a recording of a locked channel now requires the parental PIN.
- The Recordings list refreshes when a recording finishes and restores the focused row.

## 0.1.1 (version code 2)

- Restored the last-channel startup behavior when reopening the app from Projectivity after Home.
