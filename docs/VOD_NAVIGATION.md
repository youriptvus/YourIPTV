# Movies and Shows navigation - 1.1.1 / code 52

The current local candidate is 1.1.1/code52; the confirmed published baseline remains 1.1.0/code51. Phone and TV share the same production application/data/player implementation. This document describes current navigation; old candidate counts and layout findings are historical.

## Browse and open

Movies/Shows use category browsing, proportional posters and a selected-title header. TV D-pad navigation has visible focus and finite row boundaries; phones support touch, portrait layouts and short-window sizing. Focusing a title does not begin playback. Optional metadata is requested for the opened title; missing artwork/details do not prevent a valid stream from playing.

Movie details offer the available Resume/Play From Start, favorite, watched/unwatched and clear-progress actions. A series opens vertically stacked seasons with horizontal episode cards. Season/episode selection is remembered while visiting; returning from playback restores a valid card without overriding a newer user selection. Images come from authorized provider metadata/logos or neutral fallbacks; browsing does not open extra streams to generate thumbnails.

## History, episodes and playback

Main History groups episodes under one routable parent series and keeps movies separate. Selecting a series opens its seasons and episode progress. The prominent last-watched summary can differ from Continue: Continue prioritizes the most recently watched unfinished episode, or an available later episode after completion. Thin green episode bars distinguish partial and completed playback; unwatched episodes have no bar.

Resume and Play From Start act on the chosen episode; restarting one does not reset others. Manual watched/unwatched/clear choices survive delayed writes from previous playback sessions. New intentional playback remains allowed. Stable source/content identity, not matching display titles, controls progress reuse. See [progress policy](VOD_PROGRESS.md).

Autoplay Next Episode is ON when unset and preserves saved OFF. The normal Playback preference needs no Admin PIN. Confirmed natural completion can advance after a cancellable countdown across available seasons. OFF and the final episode return to the caller without looping. Manual Next remains available while autoplay is OFF. Early ending/errors retain an appropriate prompt/fallback rather than being treated as a completed episode.

## Guide and fullscreen return

Explicit guide entry opens the requested playlist/channel at the current timeslot and programme. Reopening a cached guide resets its time; ordinary refresh and active past/future browsing retain the chosen time. Cold entry retains its validated source request until rows can be published, and guarded layout/window-ready focus does not steal later user navigation.

Holding physical Back returns to the watched stream, rather than the highlighted guide row, with fresh source/parental checks where needed. Short Back keeps normal screen/level navigation. Launcher re-entry can resume the remembered live channel from any app screen, respecting its playlist and restrictions. Catch-up opens the archive player directly with its archive URL/flag intact.

Parental restrictions apply before protected metadata/playback, including external-player entry points. Selecting/restoring an item never grants authorization or overrides provider connection limits. Settings Lock defaults OFF unless a saved/protected state applies; independent Admin and parental policy remains in force.

See [remote controls](REMOTE_CONTROLS.md), [Settings security](SETTINGS_SECURITY.md), [known limits](KNOWN_ISSUES.md) and [current QA scope](QA_CHECKLIST.md). Actual test results and artifact hashes belong to the matching release sidecars.

## Historical evidence

The [preserved earlier document](history/VOD_NAVIGATION-before-1.1.1.md) retains older implementation/QA records with their original dates and limits. It does not override the maintained 1.1.1/code52 guidance above.
