# Movies and Shows navigation in 1.0.3

YourIPTV 1.0.3 / Android versionCode 34 corrects the phone layout from clean 1.0.2. A short
landscape window uses a compact selected-title/count header and poster sizing based on usable
height as well as width. Full TV presentation remains when enough space is available. The release
also corrects selected-episode/focus restoration after Series playback with touch or a remote.
The corrected final code passed 288 tests in 40 classes with zero failures/errors/skips; signed
release assembly, release vital lint, native checks, 13 archived updater fixtures and 19 retained-APK
identity audits passed. Scoped tests passed on the exact final APK on the SM-S166V/API 36 phone and
onn. 4K Pro/API 34 TV. Exact evidence and limits belong in the [QA checklist](QA_CHECKLIST.md).

The earlier SM-S166V phone check found five-column 1.0.2 Movies/Shows cards clipped before a complete
image and caption could fit. Live video and movie details/buttons/favorites worked, but actual
phone movie video/resume was not confirmed. This observed limitation motivates 1.0.3; it is not
proof of the later correction. The final 1.0.3 phone test verified complete poster/caption rows at
text scales 1.0 and 1.3, restored the original scale and confirmed actual movie video/saved resume.
Prior 1.0.2 TV and automated evidence stays historical.

An earlier private 1.0.3 candidate passed 282 tests in 40 classes, phone grid/enlarged-font/movie
checks and TV full-header/eight-column/season/episode/subtitle checks. Series return focus did not
pass on that superseded candidate. Its evidence was archived before the additional correction and
the successful final suite/build/device checks; it is not mixed with final-APK evidence.

## Browsing posters

Open Movies or Shows and choose a category. Large portrait posters replace the small vertical
catalogue list. Category/navigation panels collapse while browsing so more space is available
for posters and selected-title information above them. A wide TV layout targets around 7–8
posters per row. Short landscape phone windows use a compact header that retains the selected
title and useful count, with less artwork/header height and without a remote-only hint. Poster
sizing considers usable height as well as width so a complete proportional image and its readable
caption can fit together. Touch scrolling and current selection restoration remain. The full-size
header and TV presentation are retained when enough height is available; the exact layout depends
on the usable window rather than the device's advertised pixel resolution.

Use LEFT/RIGHT to move between posters and UP/DOWN to move between rows. A row edge does not
wrap to another row. The left edge allows returning to categories; BACK steps back through
the current detail, grid, category and menu levels. Returning from details or playback restores
the selected title and browsing position when the item is still available. If a refreshed
catalogue no longer contains that item, selection falls back to a valid nearby position.

Moving focus updates the selected-title header without starting playback. Full optional details
are requested when a title is opened, rather than for every poster passed by the remote.

## Movie details and playback

Selecting a movie opens its details page before Play. Available provider information can include
the plot, artwork, genre, year, duration or rating. Providers do not all supply the same fields;
missing or failed optional details must not prevent playback.

Existing favorites, resume and internal/external player controls remain available. Playback starts
only after the appropriate action. Parental restrictions still apply before protected details
or playback, including external-player entry points. This screen does not bypass provider
connection limits or Android restrictions.

Newly created players start with subtitles off. During playback, use the existing subtitle
controls to turn an available subtitle track On or Off. Manual selection remains available;
this change sets the initial player default and does not introduce a saved subtitle preference.

## Seasons and episodes

A series displays seasons as vertically stacked rows. Each season contains a horizontally
scrolling row of wide episode cards. UP/DOWN moves between seasons; LEFT/RIGHT moves between
episodes in the current season. Each season remembers its selected episode for the visit so
moving back to it does not always return to the first card.

Episode ordering, playback queues and resume behavior remain available. The 1.0.3 Series correction
preserves the explicitly selected season/episode when opening playback and restores that card
and focus on return with touch or remote controls. It must not jump to Favorites or another
episode, consume restoration before the card is ready, or override a newer selection. This
behavior passed scoped final phone/TV checks: plain playback and touch subtitle-menu returns kept
the exact E3 card and heading, and subsequent remote Right advanced to E4. Selection state does not grant parental access;
the current restrictions still govern opening and playing content.

## Where artwork comes from

Episode pictures come from optional provider metadata or imported M3U episode logos. Series
covers and backdrops provide a fallback when a useful episode picture is missing or unavailable.
If neither is suitable, a neutral placeholder is displayed. The provider may reuse one image
for an entire series or omit images, so different cards are not guaranteed to have distinct stills.
Provider-supplied image URLs may refer to an external image host.

The app does not generate video screenshots, seek into episodes to capture frames or open
extra playback streams while browsing. It does not infer how another player obtained its stills.
Capturing a frame during ordinary playback would be a separate future change and is not part
of this release.

Image fetching uses bounded downloads, decoded dimensions and background work. Cache entries
account for the requested display size; recycled views guard against results from an older
request appearing on a different card. Artwork loading is improved for browsing large libraries.
Missing, malformed, failed or unsupported artwork falls
back rather than preventing navigation or playback. Optional metadata is loaded on demand with
a bounded cache, not by downloading full details for the whole catalogue at startup.

## Existing installations and release handling

This is an independent YourIPTV implementation. No TiviMate code, UI assets or branding are
copied. All player features remain free; only custom rebranding is a paid offering, and its
unfinished companion integration remains paused and excluded. Adjustable buffering is also paused.

The official package and signing identity, requested permissions and GitHub/website update routes
stay the same. A compatible in-place update retains playlists and local app data. Never uninstall
or clear data to force an upgrade. The always-on Settings master/custom PIN policy and PIN-free
About from 1.0.1 remain in effect; earlier installations receive that policy when upgrading.
Read [Settings security](SETTINGS_SECURITY.md) for its limits and migration behavior.

The prepared 1.0.3 metadata uses `previousVersionCode: 33`: compatible code 33 clients can
Install or Ignore, while lower supported codes receive the existing required-update prompt.
Official clients from 0.4.1 onward using the current update address can skip intermediate
versions. The retired 0.4.0 route and older clients require a one-time manual in-place install;
Android install permission, confirmation and restart restrictions still apply.

After a successful in-app update, the existing installed-update confirmation shows the version
and four 1.0.3 release-note bullets followed by a separate credit:

**Thanks to Dylan B. for the suggestions for the current build.**

This uses the existing update receipt; no separate credit popup or update permission is added.
The four-note text/credit and receipt lifecycle passed JVM and final DEX checks. The actual automatic
download/install and successful-update popup were not device-tested; ADB installation does not
create that receipt. No fake receipt was installed for these checks.

The last recorded website mirror serves signed 1.0.2 / code 33 and its matching metadata.
The 1.0.3 authorization is local only: no server uploads or GitHub publication are included. Preserve
existing release assets and signing material. After the reviewed release commit, record source ZIP,
backup/Desktop/Repository checks in `D:\APK\backups\YourIPTV-1.0.3-PUBLISH.txt` and the local
verification folder's `final-audit.json`; those records establish actual staging status. Never
replace an existing archive. No uploads or publication were performed for 1.0.3; GitHub publication
remains the owner's manual action. The final device tests were in landscape on these two devices;
portrait/global insets, independent seek accuracy, a long live-playback soak, network-failure
injection and all providers/codecs were not tested.
