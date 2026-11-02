# YourIPTV device test report

Updated: 2026-11-01

## 0.3.3 phone fullscreen smoothness

- **Devices and build:**
  - moto g 2025 `<moto-g-serial>` (USB; Android 16; 1604x720 landscape; plays at 60 Hz). 0.3.3/code 7
    was installed with `adb install -r` over 0.2.0, keeping its data.
  - Regression check on `<onn-4k-pro-box>` (installed `-r` over 0.3.2).
- **Unit tests:** 127 tests, 0 failures (new `LiveTsExtractionTest`).
- **TiviMate reference** (same phone; channels 38 BIZARRE FOODS FHD and 40 BOUNCE TV HD, both
  1080p60; evidence in `qa_evidence\phone_fullscreen`, tm01–tm12):
  - About 60 fps presented, one vsync per frame.
  - The `c2.mtk.avc.decoder` takes about 60 inputs/s for 60 outputs/s.
  - This held with the system bars shown, after they auto-hid, and with TiviMate's own controls
    shown.
- **YourIPTV 0.2.0 before the fix** (y01–y04):
  - About 7 fps presented, whether the bars were hidden or shown, and in the guide preview.
  - The decoder took about 217 inputs/s but produced only 52–56 outputs/s.
  - A codec flush occurred about every 4 s.
  - The app used 89% CPU.
- **YourIPTV 0.3.3 after the fix** (y10–y20). All fps figures are presented frames, and every
  hardware result had 0 flushes:
  - **Fullscreen, channel 38:** 60.1 fps, with 61 decoder inputs and 60 outputs per second.
  - **System bars:** five edge-swipe show/auto-hide cycles gave 58.7–60.7 fps while shown and
    60.1 after auto-hide.
  - **Guide preview:** 60.1 fps.
  - **Channel 1 BIG BROTHER (30 fps):** steady 30 fps in the preview and in full screen.
  - **Number-entry zap to 40:** 59.6–60.1 fps with the bars hidden or shown, and with the
    banner shown.
  - **Home → Recents, and Home → launcher icon:** fullscreen resumed at 60.1 fps.
  - **Rotation:** 59.6–60.1 fps in portrait and in landscape. The original rotation settings
    were restored afterwards.
  - **Back hierarchy:** full screen → guide → categories → main menu, with the preview still
    playing.
  - **10 min 12 s continuous fullscreen, channel 40, hardware decoder** (y20_10min_hw.txt):
    sampled 59.1–60.1 fps throughout, two bar reveals at 59.7–60.1 fps, and no codec flushes in
    the logcat window covering the run.
  - **Software decoding** (`c2.android.avc.decoder`; setting temporarily On, then restored Off):
    - The decoder's inputs and outputs are now 1:1 (about 60/s).
    - Presentation alternated between steady 59.6–60.1 fps and periods of about 9 fps. This did
      not follow the bars state: it depended on the CPU load of the software decoder threads in
      `media.swcodec` at 1080p60.
- **TV box `<onn-4k-pro-box>`:**
  - Channel 38 fullscreen at 60 fps.
  - Back to the guide kept the preview playing.
  - Moving focus did not change the preview, and OK switched it to 40.
  - OK again gave fullscreen at 59.9 fps.
  - Back restored the preview.

## 0.3.2 category start position

- **Device and build:** `<onn-4k-pro-box>`; signed release 0.3.2/code 6 installed with `adb install -r` over
  0.3.1 (data kept).
- **Unit tests:** 123 tests, 0 failures. `guideIsMergedIntoLiveTv` now checks that category browsing and
  opening never position the guide at the playing channel.
- **Device results** (evidence in `qa_evidence\guide_size`, s02 and s10–s14):
  - Reproduced on 0.3.1 (s02): All channels opened at 437 CINEMANIA, the playing channel.
  - On 0.3.2:
    - Back from full screen returns to the playing channel, 437 (s10).
    - OK on All channels opens at 1 BIG BROTHER, followed by 2, 3… (s11); browsing back to All
      channels also starts there (s12).
    - OK on AM | USA | PREMIUM opens at its first channel, 253 (s13).
    - Menu → TV → All channels opens at 1 (s14).
    - Preview → full screen played one audio stream, and the crash log was empty.
## 0.3.1 compact guide

- **Device and build:** onn. 4K Pro, `<onn-4k-pro-box>` (1920x1080, density 320, default font
  scale); signed release 0.3.1/code 5 installed with `adb install -r` over 0.3.0 (data kept).
- **TiviMate measured firsthand** (same device and settings; evidence `qa_evidence\guide_size`,
  t01–t02 plus a uiautomator dump):
  - preview 320x180 dp at the top-left, with no page header;
  - timeline 28 dp, rows 39 dp, about 7.8 rows visible;
  - channel column 270 dp, with a 45x30 dp logo;
  - 160 dp per 30 minutes (about 2 h 10 min visible);
  - name text about 14 sp;
  - opening the categories slides the guide right without resizing it.
- **YourIPTV before** (y01–y02): 56 dp rows (about 4.3 visible), a 71 dp header above the
  preview, a 180 dp name column without logos, 16 sp text.
- **Unit tests:** 123 tests, 0 failures, including `liveGuideUsesCompactTiviMateProportions`.
- **Device results (all pass):**
  - Guide level (y13): about 8 rows, logos, full names, no text overlap or clipping.
  - Categories level (y14): the guide slides right at full size, as in TiviMate.
  - Menu level (y15): hourly time labels with no collisions.
  - Category-filtered guide (CINEMANIA HOLLYWOOD, y16–y17): correct channels.
  - Preview and full screen (y17–y19): OK previews the channel; a second OK opens full screen
    (`PlayerActivity`); Back returns to the guide with the same channel, category and preview.
  - Back chain, repeated twice: full screen -> guide -> categories -> menu with TV focused. One
    app AudioTrack was playing at every step, so no duplicate streams.
  - 4-digit channel numbers (For Adults, 6588+, y21) fit without clipping. Moving focus did not
    tune a channel.
  - Paging and scrolling work (y04–y05). The crash log was empty.
- **Not retested in 0.3.1:**
  - Parental PIN prompts: parental control is not enabled on this install.
  - Empty Favorites.
  - Movies and Series: their code is unchanged.
## 0.3.0 Live TV / guide merge

- **Device and build:** onn. Streaming Device 4K Pro, `<onn-4k-pro-box>`, serial
  `<box-serial>`; signed release 0.3.0/code 4, installed with `adb install -r` over 0.2.0
  without clearing data. SHA-256
  `4F6D460F6C6EEB189581DE14105EB0DE5956652A94F4F4B7B8FDC0DBC6306C50`.
- **Build:** `assembleRelease testDebugUnitTest` passed: 122 tests, 0 failures.
- **Evidence:** TiviMate reference screenshots `qa_evidence\tivimate_nav\n01`–`n22`; YourIPTV
  screenshots `qa_evidence\nav_030\01`–`39`.

| Check (device) | Result |
|---|---|
| Main menu has no TV guide / EPG entry | PASS: rail lists Search, TV, Movies, Shows, Favorites, History, Recordings, Playlists, Settings |
| Cold launch with "start with last channel" → Back → guide → Back → categories → Back → menu | PASS (fixed a bug where the first Back after launch could skip to the menu and select Favorites) |
| TV → categories; All channels → guide with all 6739 channels | PASS |
| Individual category → filtered guide ("TV · AM \| USA \| CINEMANIA HOLLYWOOD", 203 items) | PASS |
| Moving through categories re-filters the guide without tuning | PASS: preview stayed on the playing channel, one audio track |
| First OK previews another channel; second OK opens full screen | PASS |
| Full screen → Back → guide → Back → categories → Back → menu, repeated 4 times | PASS: same channel, category and focused row each time; audio track count stayed at 1 |
| Category list wraps (Favorites ↔ For Adults) | PASS |
| Hold LEFT → earlier programme (A&E East 2:00–2:30, "in archive"); Back → now; short LEFT at now → categories | PASS |
| Missing EPG ("No information" / "No programme information") | PASS |
| Empty category (Favorites): "This group is empty.", RIGHT/OK stays in categories, Back → menu | PASS |
| Locked category (temporary parental PIN + lock): browsing shows only the locked message; OK asks for the PIN; Cancel stays; correct PIN opens the guide without tuning; preview asks the PIN, then plays; full screen and Back work; browsing back over it hides the list again | PASS; PIN and lock removed afterwards (PIN Not set, Locked groups None) |
| Unavailable stream (Big Brother feed) shows "Preview unavailable…" without blocking navigation | PASS |
| Player options → TV guide opens the guide at the playing channel and category | PASS |
| Dedicated GUIDE key | Intercepted by Google TV (opens system home); not reachable by apps |
| Movies and Shows: unchanged layout, OK opens the player / series screen, Back restores the row | PASS |
| Crash buffer | Empty |

Audio dropouts of about 3 seconds were seen during navigation. An idle 6-minute watch with no input
showed the same dropouts (the provider ends the stream and the preview reconnects), so they are not
caused by the level transitions.

## 0.2.0 live-preview release

- **Device and build:** onn. Streaming Device 4K Pro, `<onn-4k-pro-box>`,
  serial `<box-serial>`; signed release 0.2.0/code 3. Installed over the existing app without
  clearing data. Project, installed, and desktop 0.2.0 release SHA-256:
  `B42663E81C656419345690FFD97A15560EAB4D14B37579A4EAF8DA10580D7190`.
- **Build:** `testDebugUnitTest assembleRelease` passed, 121 tests and 0 failures; release lint
  completed. APK signature verified; no private provider values or test configuration bundled.
- **Final APK:** actual video in Live/Guide, focus-only browsing, switching previews, separate
  delayed OK selections, Back restoration, Guide category browsing and channel-label touch
  passed (`118`-`121`). Sampled audio session 881 stayed unchanged across handoffs.
  A 93 MB temporary recording stopped/finalized, refreshed and deleted through touch options.
- **Preceding builds:** cross-PIN rejection, protected preview/full screen, restriction changes,
  restart persistence, protected recording authorization, long-delay touch, slow-load hint,
  unavailable-stream feedback and movie/episode playback were tested. These are not all
  final-APK retests; see the per-check build distinctions in the checklist.
- **Cleanup and logs:** the temporary playlist, test history, recordings, PINs, and group lock
  were removed; the original no-playlist state was restored. Retained crash, system, and event
  logs retained the final test interval (epoch `1793482424` onward) and contained no new
  YourIPTV crash/ANR matches. An older pre-fix recording-refresh crash remains in the crash buffer.
- **Not fully verified:** the exact ended-stream failure was not reproduced after the fix; physical
  remote timing and audible duplicate audio still need a hands-on check. The provider's connection
  counter was unreliable, so one actual server-side connection was not independently confirmed.
  Intermittent launcher returns also occurred during final recording navigation without a new
  crash record; their cause and final recording-row D-pad focus restoration remain unverified.

Detailed statuses, exact remaining checks, and evidence are in [QA_CHECKLIST.md](QA_CHECKLIST.md).
The sections below describe earlier builds, not a complete 0.2.0 full-app retest.

## Scope and privacy

- Device: Onn Streaming Device 4K Pro, Android 14/API 34, ADB `<onn-4k-pro-box>`. Testing was on the TV box only.
- Only the user's authorized playlist was used: its M3U link and the Xtream login from that same link. They are stored in the untracked `test-config.local.properties`.
- Credentials are left out of this report, the source, logs, and checkpoints.
- Earlier test playlists were deleted from the app on the box.
- Earlier results with other providers are historical and have been replaced by the results below.

## Results (authorized source)

### Full QA pass (onn box only, 2026-10-30/31)

The complete inventory, per-feature results, evidence IDs and defects D1–D18 are in
[`QA_CHECKLIST.md`](QA_CHECKLIST.md). All defects except D14 (slow timeout for unreachable servers,
documented) were fixed and re-tested on the box. Final build: 103 unit tests, 0 failures.

### Backup & Restore (onn box only, build sha256 639a5d19…f698, 97 unit tests passing)

| Test | Result |
|---|---|
| Create backup | PASS; file saved under `Android/data/com.youriptv.app/files/backups/`, dialog shows name and folder; content checked on the PC without printing secrets (17 settings, 2 playlists, 1 favorite, parental flags, no PIN data) |
| Restore: list, Cancel, Back | PASS; newest first with date and size; confirm defaults to CANCEL; Cancel/Back change nothing |
| Restore existing data | PASS; changed settings reverted, "0 new, 2 already on this device", no duplicate playlists or favorites |
| Restore a missing playlist | PASS; deleted Authorized Xtream, restore re-added it, reloaded channels and guide (account Active), restored its favorite; original DB then put back from a snapshot |
| Delete backup | PASS; confirm defaults to CANCEL; OK removes the file |
| Invalid file / newer-version file | PASS; clear error message, nothing changed |
| Security | PASS; parental PIN asked while Lock playlists is on; Settings PIN refused there; restore kept Block adult and Lock playlists on (backup had them off) and left the PIN unchanged |
| Save/restore via system file picker | NOT TESTED on device: the onn box only has the Android TV stub, so these entries are hidden there (unit-guarded) |

### Earlier results

| Test | Result |
|---|---|
| `.\gradlew.bat assembleDebug testDebugUnitTest -q` | PASS (exit 0; 81 unit tests, 0 failures) |
| Xtream login/account info | PASS; Active, max 1 connection, expiry shown with year |
| Xtream import | PASS; 5,733 live, 32,694 movies, 5,203 series |
| M3U import (`Keep as M3U`) | PASS; ~147k entries with load progress; 5,742 channels, 32,694 movies, 108,541 series episodes |
| EPG (Xtream and M3U) | PASS; M3U guide derived from the login URL; now/next shown in channel rows |
| Live playback, zapping, overlay, channel list | PASS (Xtream and M3U) |
| Movies and seek | PASS |
| Series: groups, "All shows", seasons, episodes, playback | PASS (Xtream and M3U; M3U Narcos S1E1 played 00:29 / 56:42) |
| Favorites (channel, series) | PASS |
| History incl. episodes and resume | PASS after DB v2 fix |
| Error handling | PASS; provider separator entries (no stream) show reconnect attempts, then an error with key hints |
| Dialog focus highlight | PASS after theme fix |
| LEFT from list goes to the selected group | PASS after fix |
| Catch-up/archive (Xtream, box VPN on) | PASS; A&E East (2-day archive) "Parent Wars" 2:30 PM played from the archive, 28:57 seekable. Without a VPN the ISP serves an HTML block page for the archive host; the player now reports this as a network filter |
| Audio delay | PASS; +150 ms, +2000 ms, −250 ms and reset applied during live playback with no interruption; options label and Settings entry update |
| DB upgrade v2 → v3 (recordings, reminders) | PASS; no crash, existing data kept |
| Recordings section | PASS; empty state, D-pad navigation, focus returns to the row after playback |
| Record channel now (M3U, US - A&E EAST HD) | PASS; duration picker (until programme ends / 30 min / 1–3 h); MPEG-TS file grows in app storage; "● Recording now" row with size |
| Play recording while recording / after | PASS after fix (local `file://` playback used the HTTP-only data source); seekable |
| Interrupted recording (app reinstalled mid-recording) | PASS; partial file kept, marked done with a note |
| Delete recording | PASS; confirm dialog defaults to Cancel; file removed; empty state shown |
| Guide reminder (🔔) and scheduled recording (●) | PASS; marks in cell and header; persisted across reinstall; scheduled recording cancelled via MENU |
| Reminder dialog firing | PASS; fired 1 min before start over the Settings screen, focus on WATCH NOW; "Watch now" played the channel; reminder removed afterwards |
| Player option "Record this channel" | PASS; duration picker. Before the connection-limit change: playback continued while the recording grew (~0.5 MB/s) and the provider tolerated both streams. After it: "One connection only" warning (Cancel default); OK closes the player, focus returns to the channel row, recording grows |
| App icon and TV banner (new YourIPTV artwork) | PASS; shown in Google TV "Your apps"; OK on it launches YourIPTV with data intact |
| Stop recording (Recordings > MENU) | PASS; "Recording stopped" toast; file stops growing; row changes to finished with size; focus kept; finished file plays; deleted afterwards |
| Auto frame rate | PASS (detection): live MPEG-TS 30 fps (estimated), movie 23.976 fps; "switch to none" because the display has a single 59.94 Hz mode; no crash; setting returned to Off |
| M3U catch-up (Xtream-panel M3U, box VPN on) | PASS; refresh reads archive days from the panel; ↺ marks on A&E East/ABC rows and past programmes; "The First 48" 4:00 PM played (59:11, fast-forward seek OK); "Play from start" on the airing programme played from 6:00 PM; "Jump back one day" offered for archive channels. Plain `catchup*` attributes: unit-tested only |
| Start with last channel (default on) | PASS; launching from the TV home screen opens the player on the last channel (A&E East) |
| Network vs provider vs app error diagnosis | UNIT-TESTED (`CatchupNetworkTest`); the ISP block page was confirmed manually from the PC |
| Crash scan | PASS; 0 `FATAL EXCEPTION`/ANR |
| Connection release | PASS; `active_cons=0` after leaving the player |
| Final audit: Xtream live as HLS (.m3u8), 8000 ms buffer, software decoding, custom global User-Agent | PASS; log `Start LIVE stream as HLS; buffer 8000 ms; sw decode true`; `c2.android.avc.decoder` in use; clean A&E East picture |
| Final audit: guide time shift +1 h | PASS; ABC "Eyewitness News at 5" shown at 6:00 PM; reset to 0 |
| Final audit: defaults restored (TS, 3000 ms, hardware decoding, no UA, shift 0, Authorized M3U active) | PASS; log `Start LIVE stream as progressive/TS; buffer 3000 ms; sw decode false`; M3U channel played |
| Final audit: installed APK = built APK | PASS; SHA-256 identical on box |
| Parental lock: Settings and Playlists | PASS; "Enter parental PIN — Settings are locked" prompt shown; wrong PIN stays on the main screen; correct PIN opens Settings / Playlists; wrong PIN cannot switch a lock off; both locks returned to Off (Settings later moved to its own PIN, below) |
| Settings lock with separate Settings PIN | PASS; parental PIN rejected as Settings PIN ("Choose a PIN that is different from the parental PIN"); "Enter Settings PIN — Settings are locked"; parental PIN refused, Settings PIN opens Settings; turned off with the Settings PIN |
| Block adult categories (parental PIN) | PASS; "For Adults" (152 channels) missing from the TV group list; All channels 5742 → 5590; search "XXX" 160 → 9 results, all ordinary VOD titles; returned to Off |
| Unit tests | 81/81 pass (including `SettingsNavigationTest`: category definitions, preservation of all 25 controls, Back/focus and lock wiring; existing PIN and data tests still pass) |
| Parental/Settings PIN audit (onn box only, build 30 Oct 8:31 PM) | PASS; see "Parental controls and Settings lock" below |

## Parental controls and Settings lock (onn box `<onn-4k-pro-box>` only)

PIN values are kept in the untracked `test-config.local.properties` and do not appear here.

| Test | Result |
|---|---|
| Create parental PIN: 3 digits, mismatched repeat | PASS; rejected, nothing saved |
| Create Settings PIN equal to parental PIN (and the reverse, on change) | PASS; "Choose a PIN that is different from the …" toast, nothing saved |
| Settings prompt: wrong, parental PIN, empty, incomplete, Cancel, BACK | PASS; Settings stay locked; "Incorrect Settings PIN · N attempts left" |
| 5 failed Settings PIN attempts | PASS; "Too many incorrect attempts … again in 30 s"; prompt not shown during lockout |
| Correct Settings PIN | PASS; Settings open with "Unlocked for this visit only…" notice |
| Relock on BACK, HOME; after force-stop/relaunch; after APK update | PASS; rail shows "Unlock Settings", reachable with the remote |
| Start SettingsActivity from outside the app (`am start`) | PASS; refused (not exported) |
| Parental prompt (Block adult, lock group, History, guide, TV list, number entry): Settings PIN | PASS; rejected |
| Parental options inside unlocked Settings still need the parental PIN | PASS |
| Change parental PIN while Settings lock is on (and vice versa) | PASS; old PIN rejected, new PIN works, the other PIN unchanged; both restored afterwards |
| Locked group: TV list, History, guide, number entry in player | PASS; prompt shown; Cancel keeps playback on the previous allowed channel |
| Start with last channel when its group is locked | PASS; not auto-played, main screen shown |
| Lock the group of a channel playing in picture-in-picture | PASS; PiP player closes |
| Block adult categories: group list, search "XXX", number entry 5592 | PASS; hidden; 9 ordinary VOD results; "No channel number 5592" |
| "All channels" count with hidden groups | FAIL → fixed (showed 5742 while 5590 listed); retested on new build |
| Legacy shared-PIN Settings lock (old `lock_settings` flag) | PASS; parental PIN required once, Cancel/wrong keep it locked, parental PIN refused as the new Settings PIN, separate PIN created, flag cleared, parental PIN no longer opens Settings |
| PINs in logcat | PASS; no app log line contains either test PIN |
| Recording of a locked channel | NOT TESTED on device (no such recording); code path checks the source channel group |
| Device reboot persistence | BLOCKED (wireless ADB on the box is not persistent; a reboot would drop the test connection). Force-stop and update persistence passed |
| Phone | NOT TESTED (TV-box-only scope; `<other-phone-serial>` not attached, `<samsung-phone-serial>` unauthorized) |

## Categorized Settings navigation (onn box only, verified 2026-10-30 21:00–21:20)

Device: `adb devices -l` → `<onn-4k-pro-box> … model:onn__Streaming_Device_4K_pro device:SNA`,
`ro.serialno` <box-serial>. Every command used `-s <onn-4k-pro-box>`. `<samsung-phone-serial>` was listed
as unauthorized and was never used; no other device was installed or tested.

Build: `gradlew clean assembleDebug testDebugUnitTest` → BUILD SUCCESSFUL, 81 tests, 0 failures,
0 errors. APK `app/build/outputs/apk/debug/app-debug.apk` (9,939,040 bytes, SHA-256 `499DC90E…7AC215`);
the SHA-256 of the installed `base.apk` on the box matched.

| Check | Result and evidence (uiautomator dumps + screenshots) |
|---|---|
| Category list first | PASS: title "Settings", 9 categories with descriptions, blue focus outline on General |
| Every category page | PASS: General (Start with last channel, Clear viewing history), Playlists (Auto-update), TV Guide / EPG (Keep past, Download future, Time shift, Update guide now), Playback (8 rows), Appearance (Channel numbers, 24-hour clock), Remote Controls (Invert channel up/down), Parental Controls (Parental PIN, Block adult categories, Locked groups, Hide locked groups, Lock playlists), Settings Lock (Lock Settings), About (About YourIPTV); the page title equals the category name |
| Back + focus restoration | PASS for all 9: Back returns to "Settings" with focus on the category just closed; Back from the list returns to MainActivity |
| Long list scrolling | PASS: Playback DOWN×8 scrolled "Default user agent" fully into view, focus stopped at the last row; UP×8 returned to Buffer size |
| Activate setting + persistence | PASS: OK on 24-hour clock Off → On (`clock_24h=true` saved, focus stayed on the row); after force-stop/relaunch it still showed On; reverted to Off |
| PIN creation | PASS: empty and 2-digit input refused ("The PIN must be 4 to 8 digits", nothing stored); parental and Settings PINs each required a repeat entry; stored only as salted hashes |
| Identical PINs | PASS: the parental PIN entered as the new Settings PIN was refused; Settings lock stayed Off |
| Settings PIN at a parental prompt | PASS: refused in Parental Controls → Locked groups ("Incorrect parental PIN") and at the main-screen locked-group prompt (group not opened) |
| Parental PIN at the Settings prompt | PASS: "Unlock Settings" refused it ("Incorrect Settings PIN · 3 attempts left"); wrong, empty and cancelled/Back entries also left MainActivity in front |
| Correct Settings PIN | PASS: opened the category list with the "Unlocked for this Settings visit only…" notice; parental rows inside still asked for the parental PIN |
| Parental controls while Settings are unlocked/locked | PASS: locked group "AM \| USA \| DOCUMENTARY" showed a lock icon and asked for the parental PIN after Settings were unlocked and relocked |
| Relock | PASS: Back out of Settings and HOME both relocked (rail showed "Unlock Settings"); still locked after force-stop/relaunch |
| External access | PASS: `am start` of SettingsActivity, with or without the unlock extra, refused (not exported) |
| Logs | PASS: no FATAL EXCEPTION/ANR for the app; neither test PIN appears anywhere in logcat |
| Not tested | Device reboot (wireless ADB does not survive it); playlist-lock and adult-block toggles were not toggled again in this pass (covered in the parental audit above); the Locked-groups picker's Save button was pressed by tap, because it is a standard Android multi-choice dialog that this change did not modify |

Prefs restoration: before this pass the box's `settings.xml` contained only the active playlist and
the guide timestamp. The earlier draft session had already lost the user's original parental PIN and
"General" group lock, which cannot be recovered because only a hash was stored. That 2-key state was
restored exactly after testing, so the box currently has **no parental PIN, no Settings PIN and no
locked groups**. Set them again in Settings → Parental Controls / Settings Lock.
## Environment notes

- The provider allows 1 simultaneous stream. Close other players before testing.
- The provider requires a non-default User-Agent for API requests; YourIPTV sends its own.
- The M3U contains `##### … #####` separator entries, which have no playable stream.