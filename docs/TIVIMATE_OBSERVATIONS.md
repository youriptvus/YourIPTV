# TiviMate functional reference — observations

TiviMate (package `ar.tvplayer.tv`, v5.0.1) is used **only as a functional reference**.
YourIPTV is an independent implementation: no TiviMate code, assets, strings, or branding are reused.
Nothing in TiviMate's free/premium split carries over: **every YourIPTV feature is free** (see
[REQUIREMENTS_CHECKLIST.md](REQUIREMENTS_CHECKLIST.md)).

Evidence levels used below:

- **[DEVICE]** — observed directly on the Onn 4K Pro test box (`<onn-4k-pro-box>`); screenshots in
  `testing\streaming-box\screenshots\` (`tvm_*.png`).
- **[OFFICIAL]** — stated on tivimate.com or the Google Play listing.
- **[3RD-PARTY]** — community guides only (troypoint.com, store.grapevine.is, iptvjournal.com,
  dualiptv.com, tvtech.support). Indicative, not authoritative.
- **[UNKNOWN]** — not observed and not documented; YourIPTV makes its own design decision.

## 1. Main navigation and layout

| Behavior | Evidence |
|---|---|
| Launch auto-resumes the last channel in fullscreen | [DEVICE] early session shots |
| Left rail: Search, TV, Movies, Shows, Recordings, My list, Settings | [DEVICE] tvm_37 |
| TV mode group column: Favorites, All channels, then provider groups | [DEVICE] tvm_37 |
| Movies/Shows: groups My list, History, All, provider categories; poster grid with rating badge | [DEVICE] tvm_38, tvm_39 |
| Show detail: backdrop, metadata, Resume SxEy, Play from beginning, external player, trailer, My list, season rows with an episode counter | [DEVICE] tvm_40 |
| Recordings: empty state "No recordings" | [DEVICE] tvm_41 |
| My list: My TV programs, My reminders, My movies, My shows (trash icon clears) | [DEVICE] tvm_42, tvm_45 |
| Search: voice-first screen (mic button, text field, history chips with clear) | [DEVICE] tvm_43, tvm_44 |
| Search result scope (channels / programs / VOD) | [UNKNOWN] — results could not be produced over ADB |

## 2. Remote control during fullscreen live playback

| Key | Behavior | Evidence |
|---|---|---|
| UP / DOWN | Previous / next channel (zap) | [DEVICE] |
| OK (short) | Playback panel: logo, now program with progress, channel no./name, next program, program-relative seek bar, prev/rew/pause/ff/next, LIVE, record, bottom row of channel-list, history and recent-channel tiles | [DEVICE] tvm_31 |
| OK (long) | Bottom control bar: Search, Channels list, Recordings, Multiview, Picture-in-picture, video track (resolution), audio track, audio delay ("0 ms"), subtitles (CC) | [DEVICE] tvm_30 |
| MENU | Same control bar as long-press OK | [DEVICE] tvm_46 |
| LEFT | Channel-list overlay: current group's channels (logo, number, now program, progress, catch-up icon), the focused channel's day schedule, and a program detail card | [DEVICE] tvm_49 |
| RIGHT | From clean fullscreen playback: the info banner (group top-left, clock top-right, logo, now program with time/progress/minutes left, channel number and name, description, next program). Same content as INFO | [DEVICE] tvm_r6 (resolved 2026-10-29) |
| BACK | Opens the TV guide (EPG grid) with a live preview window | [DEVICE] tvm_52 |
| INFO | Info banner: program title, time, progress, minutes left, channel number/name, quality badges (FULL HD, 60 FPS, STEREO), description, next program | [DEVICE] tvm_57 |
| Digits | Numeric entry overlay ("40") plus a strip of channels starting at that number; tunes automatically after a short timeout | [DEVICE] tvm_47, tvm_48 |
| BACK from the LEFT overlay | Closes the overlay **and switches to the previously watched channel** (not the focused one). Reproduced twice: 37 to 40 and 40 to 37 | [DEVICE] tvm_r7–tvm_r12 (resolved 2026-10-29) |
| Color keys | Not documented anywhere | [UNKNOWN] |
| Audio-delay control behavior (opening the "0 ms" item) | Not opened | [UNKNOWN] |

## 3. TV guide (EPG)

| Behavior | Evidence |
|---|---|
| Header: preview video, program title, time range, progress, duration, group, description, favorite star | [DEVICE] tvm_52 |
| Timeline row with current-time marker; rows: number, logo, name, catch-up icon | [DEVICE] tvm_52 |
| RIGHT at the edge pages the timeline forward (about 1 hour) | [DEVICE] tvm_53 |
| OK on a current program tunes the channel | [DEVICE] tvm_55, tvm_56 |
| The playing channel shows a ▶ marker | [DEVICE] tvm_52 |
| "No information" cells for channels without EPG | [DEVICE] tvm_52 |
| Long-press OK menu — Program: open in external player, record, custom recording, add to My list, description. Channel: favorites, assign EPG, block, hide, channel options. Group: manage favorites/blocking/visibility, reorder, copy, create group, group options | [DEVICE] tvm_34–tvm_36 |
| Meaning of the "◉" dot next to some channel names: it is **part of the provider's channel name string**, not a TiviMate marker. YourIPTV shows the same character in "US - A&E EAST HD ◉" from the same provider. The separate ↺ icon is TiviMate's catch-up indicator (third-party guides) | [DEVICE] tvm_r11, yi_41 (resolved 2026-10-29) |

## 4. Playlists, EPG sources, catch-up

| Behavior | Evidence |
|---|---|
| Playlist types: M3U, Xtream Codes, Stalker portal | [OFFICIAL] |
| Xtream: server, username, password; M3U: URL or file; optional XMLTV URL | [3RD-PARTY] dualiptv.com |
| Update interval, update on app start, separate EPG update interval | [3RD-PARTY] tvtech.support; settings categories seen [DEVICE] |
| Catch-up requires provider flags (`tv_archive`/`tv_archive_duration` for Xtream; `catchup*` attributes for M3U) | [3RD-PARTY] iptvjournal.com |
| Recording uses its own provider connection slot | [3RD-PARTY] iptvjournal.com |
| Provider connection limits are independent of the app | [3RD-PARTY]; consistent with our provider's `max_connections=1` |
| Not optimized for touch devices | [OFFICIAL] Google Play |

## 5. Authorized test provider probe

This is the user's authorized test source, and the only one used for live testing. Credentials are kept out of the docs.

- Earlier Xtream probe: active, `max_connections = 1`, formats m3u8/ts, server timezone UTC.
- Earlier M3U Plus probe: about 41 MB, 146,975 entries (5,742 live, 32,692 movies, 108,541 episodes); only 1,481 had `tvg-id`; no catch-up attributes.
- Earlier XMLTV probe: about 71 MB, duplicate channel ids across display names.
- Implementation consequence: streaming parsers plus SQLite are required; provider EPG ID coverage varies, so some channels can lack programme data.

## 6. Unknowns and YourIPTV decisions

| Unknown | YourIPTV decision |
|---|---|
| RIGHT key in fullscreen live playback | Shows the info banner (same as INFO). This now matches the observed TiviMate behavior |
| Previous-channel recall key | The LAST_CHANNEL remote key recalls the previous channel (verified on TV). BACK from the LEFT overlay only closes the overlay in YourIPTV, a deliberate choice for predictability; TiviMate recalls the previous channel there |
| Color keys | Not mapped in v1 |
| Search scope | Searches channels, movies and series by name; EPG program titles are searched too |
| Audio delay UI | TiviMate shows an "audio delay (0 ms)" control in the long-OK control bar [DEVICE tvm_30]; its exact step size is not documented. YourIPTV: "Audio delay" in player options, LEFT/RIGHT in 50 ms steps, DOWN resets, also in Settings |
| VOD resume | Implemented: resume from saved position (TiviMate shows "Resume SxEy" [DEVICE]) |
| "◉" marker | Resolved: it is provider name text, shown as-is. YourIPTV now shows its own catch-up indicator for archive-enabled channels |
| Recording UI | TiviMate offers record / custom recording from the programme menu and a Recordings section [DEVICE tvm_34, tvm_41]. YourIPTV: guide MENU → "Record programme" / "Record channel now" (duration picker), player MENU → "Record this channel", Recordings section in the rail. Its own layout and wording; free |
| Reminders | TiviMate lists "My reminders" under My list [DEVICE tvm_42]; its exact reminder lead time is not documented. YourIPTV: guide MENU → "Remind me", 🔔 mark, dialog 1 min before start ("Watch now" / "Dismiss"), listed in Recordings; in-app only (no background alarms) |
| Auto frame rate | TiviMate's setting exists [3RD-PARTY]; its switching details are not documented. YourIPTV: Settings toggle (default Off), switches to a same-resolution mode whose refresh rate is a multiple of the video rate; frame rate estimated from timestamps when the container (MPEG-TS) declares none |
| Multiview | TiviMate has Multiview in the control bar [DEVICE tvm_30]. Not built in YourIPTV: the authorized provider allows one connection, so it could not work or be tested |
