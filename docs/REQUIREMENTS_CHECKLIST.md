# YourIPTV — requirements and implementation checklist

Status legend: **DONE** (implemented and verified), **CODED** (written and compiled, but not yet
device-verified), **TODO** (planned for v1), **DEFERRED** (out of v1 scope; reason given).
The evidence for each TiviMate reference behavior is in
[TIVIMATE_OBSERVATIONS.md](TIVIMATE_OBSERVATIONS.md).
Live progress is tracked in a local task-progress file (not in the repository).

## 0. Mandatory: no premium tier

- [x] YourIPTV is free to use. Every implemented feature is available to every user.
- [x] No YourIPTV account, registration, purchase, activation code, license key, trial, or subscription.
- [x] No paywalls, "unlock" buttons, premium badges, trial timers, or artificial playlist or device limits.
- [x] No billing or licensing dependencies (no Play Billing or license-check libraries).
- [x] Features that TiviMate reportedly gates behind Premium are free here:
  - multiple playlists
  - favorites management
  - parental PIN
  - picture-in-picture
  - external player
  - last channel on start
  - EPG update settings
  - custom group locking
- [x] Still enforced, and **not** presented as premium restrictions:
  - IPTV provider credentials and account status (expired or disabled accounts are reported as provider errors).
  - Provider connection limits (`max_connections`): HTTP 401/403/509-style errors explain that the provider's simultaneous-stream limit may be reached.
  - Device and Android limits, such as codec support, PIP availability, and storage.
  - Parental-control PIN for locked groups and settings.
- [x] Automated test `NoPremiumAuditTest` scans sources, resources, the manifest, and Gradle dependencies for paywall, billing, license, or trial mechanisms — DONE (passes).

## 0a. Architecture: standalone IPTV player APK (mandatory)

YourIPTV is an IPTV media player. It is **not** a launcher, an OS replacement, or a device-management app.
A separate custom launcher (not part of this project) may be installed on the TV device to restrict access
to other apps and Android settings. YourIPTV does not depend on that launcher and works the same whether it
is started directly or through the launcher.

- [x] Independent, fully functional APK (`com.youriptv.app`), with its own complete IPTV interface: Live TV, Movies, Series, TV guide, playlists (Xtream Codes, M3U), player settings, parental PIN, and About.
- [x] No launcher/home replacement: the manifest declares only `MAIN` + `LAUNCHER` + `LEANBACK_LAUNCHER`, and no `HOME` category.
- [x] No OS modifications, root, device administrator, lock-task/kiosk, accessibility service, or system-level app restrictions.
- [x] Minimal permissions: `INTERNET` and `ACCESS_NETWORK_STATE` only. No services, receivers, or content providers.
- [x] All features are reachable inside the app with a TV remote (D-pad, OK/Select, BACK, plus optional MENU, digits, and CH+/-). No step requires Android system settings.
- [x] Accidental exit protection: BACK on the navigation rail needs a second press within 2.5 s to leave the app.
- [x] Phone compatibility kept (touch, portrait layout; `touchscreen` and `leanback` are both `required="false"`). Current test scope is the Onn Android TV box only; the Samsung phone is not part of the testing configuration.
- [x] Enforced by the automated test `StandaloneAppAuditTest` (no HOME category, no admin/service/receiver/provider, no DevicePolicyManager/lock-task/su code, permission allowlist).
- Note: Google TV intercepts the remote's GUIDE key and opens its own Live tab. YourIPTV can't override this without system privileges, which this requirement rules out. The guide is reachable from the navigation rail and from the player options instead.

## 1. Playlists and providers

| Requirement | Status |
|---|---|
| Xtream Codes login (server, user, password); account info: status, expiry, max connections | DONE (Onn TV, authorized source: 5,733 live, 32,694 movies, 5,203 series; expiry and max 1 connection shown) |
| M3U / M3U Plus by URL (attributes tvg-id/name/logo, group-title, tvg-type, EXTGRP, url-tvg) | DONE (Onn TV, authorized M3U: 5,742 channels, 32,694 movies, 108,541 series episodes; progress shown while loading) |
| Streaming import for very large lists (147k entries) into SQLite | DONE (authorized M3U, ~147k entries) |
| Multiple playlists: add, edit, delete, switch active, refresh | DONE (TV: added Xtream, switched between it and M3U) |
| Live stream format option (ts / m3u8) for Xtream | DONE (TV: HLS selected, log confirms HLS start, Xtream A&E East played) |
| Custom User-Agent | DONE (per-playlist and global; global UA now also applied to playlist/EPG/API downloads, unit-tested; playback with custom UA verified on TV) |
| Stalker portal | DEFERRED — no test portal is available |

## 2. Live TV and EPG

| Requirement | Status |
|---|---|
| Groups column (Favorites, All channels, provider groups) and channel list with logo, number, now-program progress | DONE (TV) |
| XMLTV import (Xtream `xmltv.php` or M3U `url-tvg`/manual URL), time-window filter, name auto-matching | DONE (TV: Xtream `xmltv.php`; M3U without `url-tvg` derives `xmltv.php` from a `get.php` login URL; now/next shown on mapped channels) |
| EPG time shift, past/future days settings | DONE (TV: +1 h shift moved ABC 5 PM program to 6 PM; past window verified through catch-up guide) |
| TV guide grid: channels × timeline, current-time marker, D-pad time paging, OK tunes (part of Live TV from 0.3.0) | DONE (TV) |
| Program details (title, time, description) | DONE (TV) |
| Catch-up / archive playback (archive indicator, past-programme playback via Xtream `timeshift` in the server's time zone, "Play from start", "Jump back one day", guide past window extended to the archive depth) | DONE (TV, box VPN on: Xtream and Xtream-panel M3U archive played and seeked). Without a VPN the test ISP blocks the archive host; the player reports this as a network filter, not an app/provider error |
| M3U catch-up (`catchup`/`catchup-type` = `default`, `append`, `shift`/`timeshift`, `flussonic`, `xc`; `catchup-source` placeholders; header-level defaults; Xtream-panel M3U reads `tv_archive` from the panel) | DONE (Xtream-panel M3U verified on TV); plain `catchup*` attributes unit-tested (M3uCatchupTest, CatchupNetworkTest) |
| Stream error diagnosis (network unreachable / ISP or network filter / access denied or connection limit / not available / provider error) | DONE (StreamDiagnosis + unit tests) |
| Reminders (guide MENU → "Remind me"; 🔔 mark; dialog 1 min before start with "Watch now" / "Dismiss") | DONE (TV: set, mark shown, persisted across reinstall; dialog firing — see TEST_REPORT); works while YourIPTV is running (no background alarms/notifications by design) |
| Recording (Record channel now with duration picker, scheduled programme recording, Recordings section: play while recording, stop, delete, interrupted-recording recovery) | DONE (TV, authorized M3U: live MPEG-TS recorded to app storage, played and seeked, deleted; scheduled recording created/cancelled); uses one provider connection; runs only while YourIPTV is running |
| Multiview | DEFERRED — the authorized provider allows a single connection, so multiview cannot work or be tested with it; when added it stays free |

## 3. Playback (Media3 ExoPlayer)

| Requirement | Status |
|---|---|
| Live playback of HLS and MPEG-TS | DONE (TV, authorized Xtream and M3U, MPEG-TS) |
| UP/DOWN zap through the current group | DONE (TV) |
| OK: playback panel (channel, now/next, progress, controls) | DONE (TV) |
| LEFT: channel-list overlay | DONE (TV) |
| MENU / long-press OK: options (audio track, subtitles, video track/info, aspect ratio, PIP, favorite, external player) | DONE (TV; long-press fixed) |
| INFO / RIGHT: info banner with quality info | DONE (TV) |
| Digit keys: numeric channel entry with timeout | DONE (TV) |
| BACK: leave the player (to the guide/browser) | DONE (TV) |
| Last channel on start (setting, on by default) | DONE (TV: launch opens the player on the last channel) |
| VOD: seek with skip seconds, resume position, history | DONE (TV: seek verified; movie and episode resume; Xtream and M3U episodes recorded in History) |
| Clear error messages: network, HTTP code, provider limit, unsupported codec | DONE (TV: reconnect attempts, then an error with OK retry / UP-DOWN zap / BACK hints) |
| Buffer size, software decoding fallback | DONE (TV: 8000 ms buffer + software decoding played, logcat shows c2.android.avc.decoder; setting range fixed to 500-30000 ms to match the player) |
| Audio delay (player options + Settings; −2000…+2000 ms, LEFT/RIGHT 50 ms steps, DOWN resets) | DONE (TV, AAC live channel; changes applied live without interrupting playback; PCM audio only, not passthrough) |
| Auto frame rate (Settings toggle, default Off; container frame rate or estimate from frame timestamps; switches only to a same-resolution mode whose refresh rate is a multiple of the video rate; restores on exit) | DONE (TV: live MPEG-TS detected 30 fps, movie detected 23.976 fps, no crash); the Onn box/display exposes a single 1080p 59.94 Hz mode, so an actual refresh-rate switch cannot be observed on this hardware |

## 4. Movies and series

| Requirement | Status |
|---|---|
| Movies by category, poster/logo, play, resume | DONE (TV, authorized source; movie list and playback verified) |
| Series by category → seasons/episodes (Xtream `get_series_info`; M3U episode entries grouped) | DONE (TV, authorized Xtream and M3U; "All shows" group; season picker; episode playback verified) |

## 5. Favorites, history, search

| Requirement | Status |
|---|---|
| Add/remove favorites (long-press/MENU on an item) | DONE (TV, authorized source; channel and series add/remove) |
| Viewing history with resume position | DONE (TV; history stores its own metadata (DB v2), so Xtream episodes show and resume) |
| Search channels, movies, series by name | DONE (TV) |

## 6. Settings and parental control

| Requirement | Status |
|---|---|
| Settings category selection and separate category pages | DONE (onn: all 25 existing controls in General, Playlists, TV Guide / EPG, Playback, Appearance, Remote Controls, Parental Controls, Settings Lock, About; D-pad, scrolling, Back/focus restoration and preference persistence verified) |
| Parental PIN (salted hash), lock groups, PIN prompt to open them | DONE (TV: M3U `General` locked, PIN-gated, hide/show verified) |
| Parental lock for playlist management (TiviMate "Require PIN for: Playlists") | DONE (TV: wrong PIN keeps the screen closed, correct PIN opens Playlists; turning the lock on or off needs the PIN; inactive when no PIN is set) |
| Settings lock with its own Settings PIN, independent of the parental PIN | DONE (TV: parental PIN refused as the new Settings PIN; "Enter Settings PIN" prompt; parental PIN refused, Settings PIN opens Settings; turning off needs the Settings PIN) |
| Two independent PINs (must differ), throttled attempts, temporary Settings unlock per visit, remote-reachable "Unlock Settings", legacy shared-PIN migration, restrictions enforced in player/PiP/number entry/recordings | DONE (onn box audit; see TEST_REPORT "Parental controls and Settings lock") |
| Block adult categories across the whole playlist (parental PIN) | DONE (TV: "For Adults" hidden from groups, All channels 5742 → 5590, search "XXX" 160 → 9 non-adult VOD titles; blocked groups also excluded from guide, favorites, history, and resume; keyword detection unit-tested) |
| About screen with the free-to-use notice | DONE (TV) |
| Local backup and restore of playlists, favorites, and settings | DONE (onn: create, list, Cancel/Back, restore with no duplicates, settings reverted, missing Xtream playlist re-added with working login and its favorite, delete, invalid/newer files rejected; PINs never in the file; restore keeps PINs and only adds parental restrictions; parental PIN required while playlists are locked, Settings PIN refused there. Save/restore through the system file picker is only offered where a picker exists — the onn box has none, so that path is unit-guarded but not device-tested) |

## 7. Android TV and remote

| Requirement | Status |
|---|---|
| Leanback launcher entry and banner; touchscreen not required | DONE (YourIPTV artwork as launcher icon, adaptive/round icon and 16:9 TV banner; source image in `docs/branding/`; verified in Google TV "Your apps") |
| Visible focus indicators on every focusable element | DONE (TV; dialog-theme fix restores focus highlight in all choice/MENU dialogs) |
| Full D-pad navigation; no touch-only controls | DONE (TV: live, guide, player, search, movies, shows, playlists, settings, dialogs; LEFT from a list returns to the selected group) |
| Phone layout (portrait) | CODED (layout); not tested in the current TV-box-only validation |

## 8. Quality

| Requirement | Status |
|---|---|
| Unit tests: M3U parser, XMLTV time parsing, Xtream URLs, series parsing, PIN hashing, no-premium audit, standalone-app audit | DONE (62/62 pass, incl. adult-category detection, user-agent fallback, incl. audio delay processor, M3U catch-up, catch-up network diagnosis/time zone/archive window, frame-rate matching/estimation, recording HLS/file names, Xtream-derived M3U connection limit) |
| Build `assembleDebug` and `testDebugUnitTest` | DONE |
| Device test on the Onn 4K Pro: install, navigation, Xtream and M3U setup, EPG, playback | DONE (authorized Xtream and M3U on the Onn box: live, EPG, movies, series, favorites, history, playlists, errors; no FATAL/ANR) |
| Documentation: README, REMOTE_CONTROLS, TEST_REPORT, KNOWN_ISSUES, requirements checklist | DONE (current TV-only results and limitations documented) |

## 9. Fixes from authorized-source testing (Onn TV box)

All of these were verified on the TV box:

- When adding a `get.php` link, the app asks whether to import it as Xtream or keep it as M3U.
- Deleting a playlist also removes its channels, EPG, favorites, and history.
- Confirm dialogs open with focus on a button.
- Error states respond to remote keys: OK retries, UP/DOWN zaps, and BACK exits.
- VOD seek works.
- Shows has an "All shows" group.
- Navigation-rail focus is pinned to the active section.
- The playlist expiry date includes the year.
- History records name, kind, group, and logo (DB v2), so Xtream episodes appear in History and resume.
- Dialog theme uses `windowBackground` instead of a theme-level `background`, so focus highlights are visible in every dialog.
- An M3U playlist without `url-tvg` derives `xmltv.php` from its Xtream-style login URL.
- LEFT from a channel or VOD list returns focus to the selected group.
- An M3U playlist's series count is labelled "series episodes".
- Audio delay (TiviMate control-bar feature): free, adjustable live from the player options; persisted in Settings.
- Local recordings play through `DefaultDataSource` (fixed: `file://` playback failed with a ClassCastException when only the HTTP data source was used).
- Returning from playing a recording keeps focus on the recording row (in-place refresh instead of a full section reload).
- Recording file names no longer repeat the channel name when the programme title equals it.
- Guide header: "starts in 14 min / 1 h 16 min" (was ambiguous "14" / "1:16"); a future programme shows "recording scheduled".
- Auto frame rate: MPEG-TS declares no frame rate, so it is estimated from rendered-frame timestamps over ~5 s (handles millisecond-rounded Matroska timestamps; 23.976 vs 24 kept distinct).
