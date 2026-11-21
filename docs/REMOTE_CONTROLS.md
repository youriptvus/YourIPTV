# Remote controls - 1.1.1 / code 52

Phone touch and Android TV remote input share the player. These controls retain provider, source and parental restrictions. Actual device scope is in [QA_CHECKLIST.md](QA_CHECKLIST.md); hardware/system keys can differ.

## Main screens and fullscreen return

| Input | Action |
|---|---|
| D-pad/OK | Move visible focus and open the selected item/action |
| Short Back | Step through the current screen or guide/category/menu levels; confirm exit from the main menu |
| Hold physical Back, about 0.6s | Return to the current watched stream in fullscreen; release does not also navigate Back |
| Menu / long OK | Item or playback options where available |

Held Back follows the watched stream rather than guide highlight. An available player is reused; reopening released live playback revalidates the playlist/channel and parental policy. With no valid stream, ordinary Back remains. Required update screens and Android system windows retain their own behavior. Android16 navigation compatibility permits physical Back down/up handling while normal Back still uses the AndroidX dispatcher; predictive Back animations are disabled. Play protection, signing and permissions are unchanged.

## Live guide

| Location/input | Action |
|---|---|
| TV menu: OK/Right | Open categories |
| Categories: Up/Down | Browse categories; delayed preview-list changes require the current category to retain focus |
| Categories: OK/Right | Open the chosen guide at now |
| Categories: Left/Back | Return to the menu |
| Guide: Up/Down | Change channel-row focus without tuning |
| Guide: Right | Browse later programmes |
| Guide: short Left | Browse earlier; at now, return to categories |
| Guide: held Left | Browse earlier programmes |
| Guide: OK | Current programme previews live; selecting the same channel again opens fullscreen; supported past programme opens catch-up; future programme opens details |
| Guide: Back | Return to now first when browsing other times, then categories |
| Guide: Menu/long OK | Programme/channel actions, supported reminders/recording, guide search and jumps |
| Page/Channel Up/Down | Page channel rows |
| FF/REW | Jump guide time by three hours |
| Red / Jump to now | Reset programme/time selection to now |

Explicit guide entry/re-entry selects the requested source/channel and current programme, including cold entry. A normal guide refresh retains active past/future browsing. Initial automatic category focus must not replace an explicit guide destination; later real category navigation remains available. Google TV can reserve the dedicated GUIDE key; use TV or player options > TV guide.

## Fullscreen and series

| Input | Action |
|---|---|
| Live Up/Down | Previous/next channel in the active queue |
| Live Left | Channel-list overlay |
| Live Right / Last Channel | Recall previous channel within its supported queue; overlay/number-entry precedence remains |
| OK / Info | Existing player controls / information banner |
| Digits | Channel-number entry where supported |
| Back | Leave playback or close its active control/overlay as appropriate |
| Hold Back | Hide player overlays/number entry and retain fullscreen playback |
| VOD Left/Right | Seek or move within on-screen controls as focused |
| Episode Next/Previous options, supported media/channel/page keys | Move within finite same-series bounds |
| Seasons Up/Down; episodes Left/Right | Move between season rows and episode cards |

Autoplay is independent of manual Next. It defaults ON only when unset, preserves saved OFF, and never wraps the final available episode to the first. Back can cancel a countdown. Returning from details/playback restores a valid selected item without overriding newer input.

## Settings

Up/Down and OK navigate categories/actions; Back returns from a settings page to categories and then the caller. Settings Lock starts OFF unless a saved/protected state applies. Protected visits require the current Settings/Admin policy while ON; leaving/backgrounding revokes the visit. Ordinary Playback includes autoplay without an Admin PIN. Parental checks remain independent. See [SETTINGS_SECURITY.md](SETTINGS_SECURITY.md).

## Historical evidence

The [preserved earlier document](history/REMOTE_CONTROLS-before-1.1.1.md) retains older implementation/QA records with their original dates and limits. It does not override the maintained 1.1.1/code52 guidance above.
