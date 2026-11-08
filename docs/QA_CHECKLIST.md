# YourIPTV — QA checklist (full hands-on pass)

## 0.5.6 release without pending companion integration (version code 30)

The owner confirmed this version is private staging, installed only on the authorized
ADB test box. This clean source retains the website updater fallback and excludes the
pending companion implementation, issuer pin, importer, UI and build prerequisite.
Companion work is preserved separately for the planned 0.5.7; it is not part of this release.

- [x] Clean-source offline unit suite: 210 tests in 31 classes, zero failures/errors/skips. Signed release vital lint and assembly pass.
- [x] Native verification passed: official package/label/signer, 0.5.6/code 30, minSdk 23, targetSdk 36 and unchanged seven permissions.
- [x] Compiled manifest/resources and R8 mapping contain no companion importer, branding contract, public issuer pin or companion classes.
- [x] Generate GitHub and website metadata from this exact verified APK and confirm chosen
      previously published version code with the owner (confirmed: 0.5.3 / code 27).
- [x] Desktop release files and Repository source updated only after matching verification.
- [ ] User reviews, commits/pushes Repository and publishes the matching GitHub release manually.

Verified clean APK: 3640367 bytes; SHA-256 `f9905297a3854421d14176c1bd58d252c6c59affde822e86277c6d95054b24b6`.

Previous 233-test/device/website results described the earlier companion-inclusive binary
(ed82733616e16dbf4e7b92b591365b70e5396a1d1c12f15251d5a09c845bc470), not this cleaned APK.
Those source/artifact records were preserved before cleanup. No device installation or
website publication of the new cleaned APK is claimed here. Do not use automatic updating
to replace an already installed code 30 with different code-30 bytes; that test box needs
an explicit technician installation. Preserve app data and cached update policy.

## 0.5.4 version-only release (version code 28, previous unfinished work)

- [ ] Full offline unit suite and signed release assembly pass; record the final test totals.
- [ ] Verify the signed APK as `com.youriptv.app`, label YourIPTV, version 0.5.4 / code 28,
      minSdk 23 and targetSdk 36; compare the official certificate and all seven requested permissions
      with 0.5.3. Player behavior and features should remain unchanged.
- [ ] Generate `update.json` with code 28 and `previousVersionCode` 27; verify the 0.5.4 APK URL,
      byte size and SHA-256 against the signed release file.
- [x] Source and existing updater tests verify direct-update policy: 0.5.3 / code 27 may Install or Ignore; 0.5.2 / code 26 and older
      supported updaters must update and do not require an intermediate 0.5.3 installation.
- [x] Archived original Repository 0.5.3 and independently verified its ZIP, APK, metadata and secret exclusions
      before syncing the new release into Repository.
- [ ] Install 0.5.4 on the authorized TV over network ADB using `install -r`, without USB, uninstalling
      or clearing data. Verify installed version/code, retained configuration and working playback.
- [ ] Test an in-app update from 0.5.2 after the user publishes the matching 0.5.4 release assets;
      verify the installed-version changelog and acknowledgement behavior.
- [ ] User reviews, commits and pushes Repository changes, then publishes `v0.5.4` with the matching
      `YourIPTV.apk` and `update.json` assets manually.

## 0.5.3 return to playback and update changelog (version code 27)

- [x] Full offline unit suite: 198 tests in 30 classes, no failures, errors or skips.
- [x] Signed release assembly and release vital lint pass. APK is `com.youriptv.app`, YourIPTV,
      0.5.3 / code 27, minSdk 23, targetSdk 36; official certificate and seven permissions match 0.5.2.
- [x] Ten gesture tests cover short release, 600 ms holds without repeats, repeated long events,
      canceled presses, delayed timers, lost focus and rapid separate presses.
- [x] Eleven stream-memory/return tests cover actual tuned channel, stale/disposed players, catch-up,
      fresh metadata, deleted channels, changed credentials and playlist identity.
- [x] Five update-receipt tests cover fresh install, failed/canceled update, compatibility with the
      0.5.2 installation marker, pause/process recreation, acknowledgement and installed-version notes.
- [x] Source review covers Activity and dialog dispatch, suppression of the held key's leftover
      release, normal short Back forwarding, parental checks, and required-update screen exclusion.
- [x] Archived and independently verified the original 0.5.2 repository, ZIP and release assets.
- [x] Generated `update.json`: code 27, previous code 26; size and SHA-256 match the signed APK.
- [ ] Full debug lint is clean: it still reports the existing 19 errors and 202 warnings
      (1 GestureBackNavigation, 1 NewApi, 17 UnsafeOptInUsageError); no new lint errors.
- [ ] Physical remote: hold Back in guide, categories, Settings, playlist editor, About and dialogs;
      verify current stream full screen, unchanged short Back, no extra Back on release and no retuning
      an already active stream. Check focus loss, software keyboard, channel list and player controls.
- [ ] On-device in-app update from 0.5.2: verify the 0.5.3 confirmation and readable changelog;
      pause/restart before acknowledgement, then Got it and reopen. Verify fresh installs and failed
      updates never claim success, and required updates cannot be skipped by holding Back.
- [ ] User reviews, commits and pushes the Repository changes, then publishes `v0.5.3` manually.

## 0.5.2 Settings lock protects playlist editing (version code 26)

- [x] Full offline unit suite passes: 172 tests, no failures or skips; signed release build passes.
- [x] Signed APK is `com.youriptv.app`, label YourIPTV, version 0.5.2 / code 26, minSdk 23,
      targetSdk 36; official signing certificate and all seven requested permissions match 0.5.1.
- [x] Generated `update.json` has code 26 and `previousVersionCode` 25; size and SHA-256 match.
- [x] Source review confirms the editor checks Settings lock before loading credentials, on resume,
      and before saving; both Playlists edit actions and the Settings shortcut also enforce it.
- [x] Archived the original 0.5.1 repository and verified its ZIP, APK, metadata, and secret exclusions.
- [ ] Full debug lint is clean. Current result: 19 existing errors and 202 warnings. Error sources
      (`PlayerActivity`, `Repository`, `AudioDelayProcessor`, `LiveTsExtractors`) are unchanged by this fix.
- [ ] On a TV and phone, enable Settings lock with parental playlist locking off; verify Edit current,
      the playlist menu's Edit action, and the temporarily unlocked Settings shortcut cannot open editing.
- [ ] Open an existing editor with Settings lock off, enable the lock from another Settings visit,
      and confirm returning to the editor cannot save changes. Also check M3U confirmation callbacks.
- [ ] Turn Settings lock off and verify M3U/Xtream editing works, the independent parental playlist
      lock still applies, and Add playlist plus playlist/EPG refresh remain available.
- [ ] User reviews, commits and pushes the Repository changes, then publishes `v0.5.2` manually.

## 0.5.0 version-only release (version code 24)

- [x] Full offline unit suite passes: 171 tests, no failures or skips; signed release build passes.
- [x] Signed release verified as `com.youriptv.app`, label YourIPTV, version 0.5.0 / code 24;
      official signing certificate and requested permissions match 0.4.3.
- [x] Generated `update.json` has code 24 and `previousVersionCode` 23; APK size and SHA-256 match.
- [ ] User reviews, commits and pushes the repository changes, then publishes `v0.5.0` manually.
- [ ] After publication, test the 0.4.3-to-0.5.0 in-app update on an authorized device.

## 0.4.3 playlist editing (version code 23)

- [x] Full offline unit suite passes: 170 tests, no failures or skips.
- [x] Signed release build verified as `com.youriptv.app`, version 0.4.3 / code 23; official
      signing certificate and requested permissions match 0.4.2.
- [x] Generated `update.json` for code 23 with `previousVersionCode` 22; APK size and SHA-256 match.
- [ ] On an authorized phone and TV, edit M3U and Xtream settings, save/reload, and verify values
      persist after reopening the editor.
- [ ] With playlist locking enabled, confirm Settings → Playlists → Edit current playlist requires
      the parental PIN; check narrow-screen phone layout and TV D-pad navigation.
- [x] Source was committed and pushed, and `v0.4.3` was published as the Latest release.
- [x] On the authorized onn TV at 192.168.1.150, the genuine 0.4.2 app downloaded and installed 0.4.3
      after Install was selected once; it reopened automatically, retaining backed-up configuration.

## 0.4.2 version-only automatic-update test (historical; standalone release not published)

- [x] Archive the complete 0.4.1 repository, changelog and signed release files locally before editing.
- [x] Offline signed release build succeeds as `com.youriptv.app`, version 0.4.2 / code 22.
- [x] All 10 focused updater tests pass, including optional update 21 -> 22 and required update for code 20.
- [x] APK has the same official signer, app label and seven requested permissions as 0.4.1.
- [x] Generated and verified `update.json`: code 22, previous code 21, size 3,632,496 bytes,
      SHA-256 `ed8d5cb4201619a8ea65cd83f4c63f93e014f428eb9443c5ae743c25dc8e9b3c`.
- [ ] The planned 0.4.1-to-0.4.2 production update was not tested because the standalone 0.4.2
      release was not published. The later 0.4.2-to-0.4.3 retest is recorded above.

## 0.4.1 website and update-source migration

- [x] Offline release build succeeds as `com.youriptv.app`, version 0.4.1 / code 21, official signer.
- [x] 164 unit tests pass, including the default About website resource check.
- [x] APK resources contain `https://youriptv.us`; README links to the same URL.
- [x] Update manifest points to `youriptvus/YourIPTV`; no connection to GitHub was made for this work.
- [x] User published `v0.4.1`; public manifest and APK URLs return 200 and match the signed local release.
- [x] onn on the code-20 updater test build installed 0.4.1 through GitHub, retained data and grants,
      and restarted automatically without an installer confirmation or new permission prompt.
- [x] Moto G upgraded from 0.4.0 by `adb install -r`; installed APK hash verified, startup and advancing
      video preview checked. Full-screen playback and all player features were not tested in this pass.

## 0.4.0 customer-box automatic updates

Evidence: `D:\APK\qa_evidence\040\` (local only). Device: onn 4K Pro, Android 14. Test builds
0.4.0-test14/15/16 used a local update server (`adb reverse`); the final step installed the real 0.4.0.

- [x] 163 unit tests pass (updater parsing/policy/no-Settings-intents, standalone audit, backup,
      settings navigation, no-premium).
- [x] Signed release `com.youriptv.app` 0.4.0 / code 20, official signing certificate.
- [x] Technician setup in PIN-protected Settings → App Updates: install permission (a02–a06), restart
      permission (e02–e05); Settings stays unlocked on return; Automatic updates toggle.
- [x] Offer dialog with Install/Ignore (b01, e06); Ignore → offered again at next opening.
- [x] Without `UPDATE_PACKAGES_WITHOUT_USER_ACTION` Android asked to confirm (b02/b03); after adding it
      the install was silent (c02). Cancel → "Update failed … cancelled" with Retry/Close (b04).
- [x] Silent install 15 → 16 with automatic restart (logcat `BAL_ALLOW_SAW_PERMISSION`), installer of
      record `com.youriptv.app`.
- [x] Required update (device 16, previous 18): Install only (g01); Back → home; reopening re-forces;
      offline reopening re-forces; offline Install → "Update failed. Could not reach the update server."
      with Retry only (g02); Retry → real 0.4.0 installed and restarted.
- [x] Data intact after every update (5742 channels, same last channel); both grants still allowed.
- [ ] Android 15+ (no auto-restart) and Android ≤ 11 (Install confirmation) not device-tested.
- [x] Published GitHub `v0.4.0` assets (`YourIPTV.apk`, `update.json`) downloaded from the
      `releases/latest/download/` URLs match the locally verified APK (SHA-256 `4b648c59…59faac4`).

## 0.3.9 integrity hardening and branding centralization

- **Decision recorded:** all player features are free; custom rebranding (via a separate, not-yet-built
  tool) is the only paid offering. See `docs/REQUIREMENTS_CHECKLIST.md` §0/§0b and `docs/BRANDING.md`.
- **Acceptance criteria (0.3.9 branding prep):**
  - [x] **TV (onn, `192.168.1.150:5555`):** default build shows name "YourIPTV", Leanback label
        `app_name`, icon `mipmap/ic_launcher`, banner `drawable/banner`; nav-rail header, Settings →
        About entry and About screen show "YourIPTV" (no `%1$s` leak), the free-for-everyone notice and
        open-source acknowledgements are intact; live playback works. Evidence:
        `D:\APK\qa_evidence\f01_tv_settings_about_category_039.png`, `f02_tv_about_screen_039.png`,
        `f02_tv_about_ui.xml`, `f03_tv_playback_039.png`.
  - [x] **Phone (moto g `ZA223G5NBZ`, Android 16):** signed 0.3.9 installed with `adb install -r`
        over 0.3.8 (data kept). Integrity gate passed and live playback resumed; app drawer shows
        label "YourIPTV" with the default icon; nav-rail header "YourIPTV"; Add playlist notice
        reads "YourIPTV is free. …" (no `%1$s` leak); the editor was closed without saving and the
        playlist list is unchanged. Evidence: `D:\APK\qa_evidence\f05_phone_main_039.png`,
        `f07_phone_after_unlock.png` (nav header), `f08_phone_launcher_039.png`,
        `f08_phone_launcher_ui_039.xml`, `f09_phone_add_playlist_notice_039.png`.
  - [x] **Phone About (after the user unlocked Settings):** Settings → About category "App
        information and free-use notice", entry "About YourIPTV"; About screen title "About
        YourIPTV", version line, "YourIPTV is free.", brand name in every body line, open-source
        section present, no `%1$s`, no company block. Evidence:
        `f10_phone_settings_about_entry_039.png`, `f11_phone_about_screen_039.png`,
        `f11_phone_about_ui_039.xml`.
  - [ ] **Phone — not covered:** the welcome text only shows when no playlist exists and was not
        checked to avoid clearing data. It uses the same `Branding.displayName`/`freeNotice` code
        verified elsewhere.
  - [x] All brand values resolve from `res/values/branding.xml` + `ui/Branding.kt`; no brand string is
        hardcoded in player screens (`BrandingCentralizationTest`).
  - [x] No payment screen, "Unlock Premium" control, or branding control appears in player settings.
  - [x] Branding changes do not read/write settings, playlists, provider credentials, PIN hashes, or
        history.
  - [x] `NoPremiumAuditTest` still passes (154 tests, 0 failures).
  - [x] Package id `com.youriptv.app` and signing key unchanged (signer `4aef8798…cecd0539`, v1+v2
        verified); integrity gate still starts the app on the onn box.

## 0.3.6 / 0.3.7 live recovery and phone landscape

- **Devices and build:** moto g 2025 (`ZA223G5NBZ`, Android 16) and onn. Streaming Device 4K pro
  (`192.168.1.150:5555`); signed 0.3.6 (code 10), then 0.3.7 (code 11), installed with
  `adb install -r` and the official signing certificate. No uninstall or data clear.
- **Automated:** `testDebugUnitTest assembleRelease` passed. `LiveBufferingRecoveryTest` covers
  the 30-second continuous-buffering deadline, three quick reopens, the 30-second slow retry that
  continues indefinitely, the playback stability reset (10 seconds from 0.3.8), shared error/ended budget, and
  cancellation on pause, channel change and manual retry.
- **PASS — moto g network outage (0.3.6):** Bizarre Foods full screen; Wi-Fi and mobile data were
  switched off for about 2 minutes. YourIPTV showed three quick reconnects, then the "Trying again
  in 30 seconds" notice with further reopen attempts. After the network was restored, video and
  audio resumed within about 5 seconds with no user input and the notice cleared. Wi-Fi and mobile
  data were restored to their original enabled state. Evidence: `qa_evidence\live_recovery_036\moto_outage_036.txt`.
- **Not reproduced:** the original natural stall after long viewing. The outage test produced
  network errors, not a silent buffering stall; the 30-second stall deadline is unit-tested.
- **onn. box, Bizarre Foods on "Test list" (0.3.7 → 0.3.8):**
  - Cause found: the provider closes this channel's connection every 15–75 seconds
    ("Live stream ended"); a reopen 1.5 seconds later normally plays. Occasionally the provider
    refuses the reopen (HTTP error) or drops every new connection for several minutes.
  - 0.3.7 FAIL: the quick-reopen budget was only refunded after a full minute of playback, so
    after a few drops each later drop waited 30 seconds; the guide preview stopped completely
    after two refused reopens ("Preview unavailable … Choose another channel").
  - 0.3.8 PASS: 12-minute fullscreen soak with audio on in nearly every sample; each drop
    reopened in about 1.5 seconds. A further 10-minute run included a ~5-minute provider outage
    (every connection refused, attempts 4–10 every 30 seconds, including one that buffered with
    no data); playback resumed automatically at 23:39 and later drops were again reopened
    within seconds. No crash or ANR. Evidence: `qa_evidence\live_recovery_036\onn_soak_037.txt`,
    `onn_soak_038.txt`, `onn_soak_038_fullscreen.txt`, `onn_recovery_stream_038.txt`.
  - The final 0.3.8 build was installed on the onn. box only; the moto g was disconnected and
    still has 0.3.7.
- **TiviMate reference:** Bizarre Foods played normally in TiviMate on the onn. box for about two
  minutes; its automatic restart was not observed.
- **onn. box playlist:** the existing Xtream playlist reported the provider account as expired with
  0 channels (last refreshed while 0.3.4 was installed). The authorized test M3U was added as
  "Test list" and made active; the Xtream playlist was left unchanged.
- **PASS — phone landscape (0.3.7):** with the moto g held upright and auto-rotate on, the main
  menu, categories, guide with preview, full-screen player, Playlists, Add playlist and a series
  screen all displayed in landscape (`mCurrentRotation=ROTATION_90`). Settings and About were not
  opened on the phone because Settings is PIN-locked. Evidence: `qa_evidence\landscape_037\`.

## 0.3.4 phone Back navigation

- **Device and build:** moto g 2025, ADB serial `ZA223G5NBZ`; Android 16; 0.3.4 (version code 8)
  installed over 0.3.3 with `adb install -r`, preserving app data and playlist.
- **Regression reproduced on 0.3.3:** after leaving fullscreen, further Back presses could leave
  the screen at the guide when Android was in touch mode.
- **PASS — hardware/ADB Back events:** full screen → guide → categories → main menu, with touch
  mode still active and TV focused in the menu.
- **PASS — Android system Back button:** returned from the guide to categories, then from
  categories to the main menu.
- **PASS — touch interaction:** one tap on All channels opened the guide; a tap selected BIG
  BROTHER for preview, a second tap opened it full screen, and Back restored the guide with the
  same channel and category.
- **Playback:** one active YourIPTV audio track was observed during preview/full-screen return.
  Evidence screenshots: `qa_evidence\phone_back\05`–`10`.

## 0.3.3 phone fullscreen smoothness

This earlier device test covered the moto g fullscreen playback fix in 0.3.3.

## 0.3.1 compact guide

See TEST_REPORT.md "0.3.1 compact guide" for the guide sizing comparison with TiviMate and the device results.
## 0.3.0 Live TV / guide merge

See TEST_REPORT.md "0.3.0 Live TV / guide merge" for the device results. The rows below that
mention the TV guide nav-rail item and `GuideActivity` describe builds before 0.3.0. From 0.3.0
the guide is part of the TV section, and `GuideActivity` has been removed.

## 0.2.0 live-preview release validation

The original full-app inventory and 0.1.0 results below are historical; they are not a claim that
every feature was retested on 0.2.0. This release pass concentrated on live previews and related
regressions. Only the onn. Streaming Device 4K Pro at `<onn-4k-pro-box>` was used for this pass;
hardware serial `<box-serial>` was checked before installation.

- **Final build:** signed release 0.2.0 (version code 3), installed with `adb install -r`.
  The project APK, installed APK, and `<Desktop>\YourIPTV-0.2.0-release.apk` have SHA-256
  `B42663E81C656419345690FFD97A15560EAB4D14B37579A4EAF8DA10580D7190`.
- **Automated:** `testDebugUnitTest assembleRelease` passed; 121 tests, 0 failures.
  The ended-stream checks in `QaRegressionTest` are source-level guards, not device playback tests.
- **Evidence:** screenshots are in `qa_evidence\youriptv_preview\`. TiviMate observations
  and evidence are documented separately in [LIVE_PREVIEW_REFERENCE.md](LIVE_PREVIEW_REFERENCE.md).
- **Data:** only the authorized provider was used. The temporary test playlist and its test
  history, temporary recordings, temporary PINs, and the temporary group restriction were removed;
  the box was returned to its original no-playlist, no-PIN, unlocked-group state.

| Check | Status | Method and evidence |
|---|---|---|
| Live TV grid, first selection, focus-only movement, channel switch, second selection, and Back | PASS | Final `B426…` APK; first OK previewed A&E East, DOWN focused West without retuning, OK switched preview, a separate OK after 12 seconds opened full screen, and Back restored identical focused-row bounds (`118_clean_release_live_preview`). |
| Browse categories without retuning | PASS | Tested on preceding 0.2.0 builds; final Guide category test retained A&E East while changing to Documentary. Live TV category selection and grid loading also passed on final APK. |
| Separate Guide, focus-only movement, switch preview, later full-screen selection, and Back | PASS | Final APK; D-pad tests switched West to East, delayed OK opened full screen, and Back restored the selected programme metadata (`119_clean_release_guide_preview`). |
| Touch/click, including a selection after a long pause | PASS | Final Guide: touching the channel label previewed Destination America; a second touch after 12 seconds opened full screen; Back restored preview (`121_clean_release_guide_touch`). A future-programme touch opened details without retuning (`120_clean_release_guide_category`). Live touch and 169-second delayed handoff were tested on preceding builds, not repeated on the final APK. |
| Long-delay handoff after the earlier black-screen defect | PASS | The preceding 0.2.0 test build showed preview video after 169 seconds and full-screen video after the later touch (`25`-`27`); final APK also passed delayed D-pad handoffs. This does not prove that the provider had emitted `STATE_ENDED`. |
| An actually ended live stream and exhausted retries | BLOCKED | The authorized provider did not reproducibly emit the original ended state during the retest. Retry/seek and terminal feedback have source-level regression guards only. To verify on device, let an authorized live stream end naturally, select it again, and check reconnection or the explicit retry message. |
| Unavailable stream and navigation | PASS | Earlier 0.2.0 build tests (`112_final_unavailable_preview`, `113_final_unavailable_fullscreen`) displayed retry/Back guidance. Final APK initially resumed an unavailable Big Brother feed, returned to the navigable listing, then played A&E. The exact natural ended-stream condition remains blocked below. |
| Separate PINs and protected previews | PASS | Preceding 0.2.0 build tests rejected each PIN at the other protection and verified restricted previews/full screen, restriction changes, and persistence. Latest protected-recording fix was separately device-tested (`108_final_protected_recording`). These security tests were not repeated after removing diagnostic-only focus logs for the final build. |
| PIN changes and restart persistence | PASS | Immediately preceding 0.2.0 test build; changed each test PIN independently, verified old/new behavior, restarted the app, and confirmed the protected channel and Settings remained locked. Original state restored. |
| One-connection recording interaction | PASS | Preceding builds verified preview stopping and blocking another stream during recording. Final APK: a sequential A&E test recording grew to 93 MB, Stop finalized it and refreshed the list, Delete removed it, and zero recordings were confirmed. Exact final-build D-pad focus restoration was not established; touch/long-press options worked. |
| Repeated handoff audio resources | PASS | Final APK Live and Guide handoffs sampled one active YourIPTV audio track, session 881 retained across handoffs and channel switches. Earlier builds also passed repeated-cycle tests. This is sampled system evidence, not audible verification or a provider connection count. |
| Movie and series paths | PASS | Earlier 0.2.0 builds played a movie and an episode in their existing full-screen players and returned to listings. Not repeated on final APK; the final change removed recording-focus diagnostic logs only. |
| Version and data cleanup | PASS | Installed package reports 0.2.0/code 3. Final recording and sole `QA Preview Temporary` playlist deleted through UI; parental PIN Not set, all group restrictions Off/None, Settings Lock Off; cold restart shows No playlist (`122_clean_release_empty_restart`). No uninstall/data-clear performed. |
| App crash and ANR scan | PASS | Final interval begins at device epoch `1793482424`. Crash buffer has zero newer entries; system/events retain 1658/2055 interval lines with no YourIPTV crash/ANR matches. Older pre-fix ClassCastException remains historical, not a final-build crash. |
| Physical remote and audible duplicate audio | NOT TESTED | ADB key/touch injection cannot establish physical-key repeat timing or what a listener hears. Manually repeat Live and Guide preview/OK/Back with the onn. remote, including long-press OK, and listen for overlapping or missing audio. |
| Provider-side simultaneous connection count | BLOCKED | The provider's `active_cons` response did not reflect the observed playback. Use a trustworthy provider dashboard while repeating preview/full-screen/Back; expect one connection. |
| Full settings-category and full-app regression on 0.2.0 | NOT TESTED | Prior full-app results below are for earlier builds. Repeat the broader checklist on 0.2.0 if a new full-app certification is required. |
| Intermittent return to Google TV Home / recording D-pad focus | BLOCKED | Home appeared during final recording navigation too, without a new crash/ANR record. Its cause and exact recording-row D-pad restoration remain unverified. Recheck with physical remote untouched and lifecycle/input logging. Do not interpret successful touch actions as a D-pad pass. |

### Preview defects addressed

- **Black full-screen handoff after a preview stream ended:** the first 0.2.0 test build opened
  black after a long wait (`08_delayed_fullscreen`). Ended/idle live streams now seek and reconnect.
  A 169-second handoff produced video on the next build, but the original ended state was not
  independently reproduced; see the blocked check above.
- **Ended full-screen stream could exhaust retries silently:** source inspection found no final
  message or effective seek on manual retry. The final build now displays reconnection/terminal
  feedback and seeks before retrying an ended stream; source-level regression checks pass.
- **Recording refresh crash and stale status:** fixed unsafe RecyclerView parent traversal,
  completion-driven refresh, section/input-generation guards, and layout-timed focus restoration.
  Stop/finalization/deletion were device-tested; see the outstanding remote-focus limitation.
- **Recording input interrupted during Stop:** handle the specific null-input interruption only
  when Stop was requested; unexpected errors remain surfaced. Functional regression tests pass.
- **Locked-group cancellation:** cancelling reauthorization falls back to a safe listing.
- **Protected recording bypass:** starting/scheduling a locked channel now requires the parental
  PIN. Wrong Settings PIN/cancellation and correct parental authorization were device-tested.

## Historical full-app pass (0.1.0)

- **Device:** onn. Streaming Device 4K Pro only. ADB `<onn-4k-pro-box>`; identity checked with
  `getprop ro.serialno` = `<box-serial>` before every install. No other device was used.
- **Build tested:** `app/build/outputs/apk/debug/app-debug.apk`, versionName 0.1.0 (versionCode 1). Final clean build SHA-256 `19F84057AE51BE7643E61E2B314B7C8848A5CEA6D9EF0FF94770B96B853900DE`,
  installed with `install -r` (data preserved) on the onn box; the on-device hash matches. Final checks on that build:
  live resume of the last channel (final1), D17 list-end focus (final2), crash/ANR buffer empty.
  It was installed with `adb install -r`, which keeps app data.
- **Data:** only the authorized playlists, "Authorized M3U" (active) and "Authorized Xtream". No
  credentials or PINs appear in this file, the screenshots or the logs. Screenshot j03 showed a
  URL with a login, so it was deleted.
- **Input:** remote-equivalent key events (`adb shell input keyevent`: D-pad, OK, Back, MENU,
  digits, media keys). Results were confirmed from the UI hierarchy (focus and text),
  screenshots, saved preferences and logcat.
- **Evidence:** screenshots are in `qa_evidence\` (IDs below: a–k series).
- **Statuses:**
  - **PASS**: verified on the device.
  - **FAIL→FIXED**: a defect found on the device, fixed, and then re-tested on the device.
  - **AUTO**: covered only by unit or source tests.
  - **BLOCKED**: could not be run, with the reason given.
  - **NOT TESTED**: not run in this pass.

## 1. Inventory (implemented screens and features)

| Screen | Features found in code and UI |
|---|---|
| Main (`MainActivity`) | Nav rail with Search, TV, TV guide, Movies, Shows, Favorites, History, Recordings, Playlists, Settings / Unlock Settings. Groups and items lists, channel-number entry, item MENU (favorite, guide for group, record now, external player, lock/unlock group), Back-twice exit, start with last channel. |
| Player (`PlayerActivity`) | Live and VOD playback, UP/DOWN zap (invertible), digit entry, banner and info, channel list, player menu (audio, subtitles, quality, aspect, audio delay, stream info, reload, record, PiP, guide, external player, favorite, previous channel, next/previous episode), catch-up / archive playback, error diagnosis, single active player. |
| TV guide (`GuideActivity`) | Grid with now-line, programme MENU (watch, details, remind, record, play from start, play from archive), jump ±1 day / now, choose group, catch-up markers. |
| Series (`SeriesActivity`) | Seasons and episodes, play, external player. |
| Playlists (`PlaylistsActivity`, `PlaylistEditActivity`) | M3U and Xtream add/edit, validation, make active, refresh, update guide only, delete, provider account info. |
| Settings (`SettingsActivity`) | 10 categories: General, Playlists, TV Guide/EPG, Playback, Appearance, Remote Controls, Parental Controls, Settings Lock, Backup & Restore, About. |
| Recordings / reminders | Record now or for a programme, in-progress playback, stop, delete, interrupted-recording marker, reminders. |
| Backup & Restore | Create a local JSON backup, restore with confirmation summary, delete, PIN-protected parental merge. Save/open via the system picker appears only when a real picker exists. |
| Not implemented (deferred, documented) | Stalker portal (no test portal) and multiview (the provider allows one connection). |

Placeholders: none found. No premium tier, paywall, trial, licence check or upgrade prompt exists in
the code or UI.

## 2. Results

### Startup and lifecycle
| Test | Status | Evidence |
|---|---|---|
| Cold start with "Start with last channel" opens the player on the last channel (#28 after force-stop) | PASS | b01, d01, k48 |
| HOME during playback, then relaunch: the player resumes on the same channel | PASS | k47 |
| Last channel in a locked group does not autoplay after relaunch | PASS | k28 |
| Back in the player returns to the main list, focused on the playing channel (D3) | PASS | d02, k48 |
| 20 consecutive zaps (#8→#28): every channel plays; 1→2 activities, PSS 95→110 MB during playback | PASS | k46, meminfo |
| No YourIPTV crash or ANR during this pass: crash buffer empty, no new dropbox entries | PASS | logcat -b crash, dropbox |
| Device reboot persistence | BLOCKED | Wireless ADB does not survive a reboot on this box |

### Live playback and player
| Test | Status | Evidence |
|---|---|---|
| Live playback (VPN on box), UP/DOWN zap, digit entry ("18" → #18) | PASS | b01–b02, e33–e35 |
| Banner and info (RIGHT), player menu, audio tracks, aspect Fit/Stretch saved, subtitles "none" message, quality, audio delay ±/reset, stream info, reload | PASS | b03–b04, c02, d03–d16, e32 |
| Choosers mark and focus the current value (D2) | FAIL→FIXED | d04/d05 |
| PiP: a second player opened while PiP was active (D4, High) | FAIL→FIXED | e23 → e29 |
| External player: the system stub hid the app's message (D5) | FAIL→FIXED | e26/e27 → e30/e31 |
| Guide from the player opens on the current channel; Back resumes | PASS | e24/e25 |
| Without VPN: connection reset diagnosed as a network/provider issue, not an app fault (D1 wording fixed) | FAIL→FIXED | a01–a06 |
| Invert channel up/down ON: UP #8→#9; OFF: UP #9→#8 | PASS | log |
| Physical-remote-only keys (GUIDE, CH±, long-press OK on real hardware) | NOT TESTED | ADB key events only. Manual test: press each key on the onn remote in the player and the guide. |

### Main screen, search, favorites, history
| Test | Status | Evidence |
|---|---|---|
| Groups and items lists, counts, rail navigation, LEFT returns to the active rail item | PASS | multiple |
| UP/DOWN at a list end jumped into the rail (D17) | FAIL→FIXED | k39 |
| Search: no results, 300 results, play result and Back restores focus | PASS | h05–h08 |
| Search hint and Enter focus (D10, D11) | FAIL→FIXED | h21, h22 |
| Re-entering Search showed the hint while the box still held the query (D15) | FAIL→FIXED | k30 |
| Favorites add/remove, empty state (D7 focus fix), guide for group focuses that channel (D6) | FAIL→FIXED / PASS | f01–f07 |
| History list, MENU, clear (CANCEL default), play from history | PASS | h01–h04 |

### Movies, shows and series
| Test | Status | Evidence |
|---|---|---|
| Movies groups, play, pause, +30 s seek, resume position | PASS | h09–h14 |
| Shows: seasons and episodes, episode plays | PASS | h15–h20 |
| No manual next/previous episode (D12, Medium) | FAIL→FIXED | h23–h32 |

### TV guide and catch-up
| Test | Status | Evidence |
|---|---|---|
| Guide opens on the last channel with the now-line and catch-up icons | PASS | i01–i02 |
| Programme MENU, details, reminder set/cancel, record with single-connection warning | PASS | i03–i08 |
| **Catch-up archive playback** (box VPN on): 59:02 archive from the programme start, pause | PASS | i09–i11 |
| Jump −1 day / now / +1 day (empty day handled) | PASS | i12–i15 |
| Watch live from the guide, zap in the player | PASS | i18 |

### Recordings
| Test | Status | Evidence |
|---|---|---|
| Duration chooser, single-connection warning, record, play in progress, stop, play finished, delete (CANCEL default) | PASS | e10–e21 |
| Recording interrupted by an app kill is marked "YourIPTV was closed while recording."; delete | PASS | k34 |

### Playlists
| Test | Status | Evidence |
|---|---|---|
| List, MENU, edit then Back discards, empty-URL validation, unreachable server message | PASS | j01–j07 |
| A failed new playlist replaced the active playlist (D13, Medium) | FAIL→FIXED | j08 → j12/j13 |
| Switch M3U ↔ Xtream, update guide only, delete confirmation | PASS | j09–j15 |
| Unreachable non-routable server takes about 2–3 min to time out (D14, Low) | OPEN | Documented in KNOWN_ISSUES |

### Settings (category navigation and effects)
| Test | Status | Evidence |
|---|---|---|
| Category selector first; each page holds only its own settings; Back restores category focus | PASS | k-series, SettingsNavigationTest |
| Channel numbers / 24-hour clock applied and saved | PASS | g04, g08 |
| Block adult categories: Search 4→0, All channels 5742→5590, "For Adults" group hidden | PASS | k36, k37 |
| Hide locked groups: NEWS hidden from lists and Search (5501) | PASS | k38, k40 |
| Lock playlists: opening Playlists asks for the parental PIN; Cancel stays out | PASS | k41 |
| Invert summary described the wrong direction when Off (D18) | FAIL→FIXED | k44, k45 |

### Parental controls and Settings lock (separate PINs)
| Test | Status | Evidence |
|---|---|---|
| Parental PIN create and repeat | PASS | k01–k02 |
| Settings PIN at a parental prompt is rejected ("Incorrect parental PIN") | PASS | k03 |
| Parental PIN cannot be used as the Settings PIN | PASS | k08 |
| Parental PIN at "Unlock Settings" is rejected; empty input and Cancel keep Settings locked | PASS | k11 |
| Correct Settings PIN unlocks for this visit only; it relocks after exit and after relaunch | PASS | k35 |
| Unlocking Settings does not unlock parental edits | PASS | log |
| Locked content asks for the PIN from Search, guide live, guide catch-up, History, Favorites | PASS | k16, k19, k20, k25, k27 |
| "Open in external player" bypassed the parental lock (D16, High) | FAIL→FIXED | k26 → k31–k33 |
| Locks persist after force-stop and APK update | PASS | k28, k35 |
| Throttling after repeated failures (5 tries, then 30 s lockout) | AUTO | unit tests |

### Backup and restore
| Test | Status | Evidence |
|---|---|---|
| Create, list, restore (summary, CANCEL default), delete, invalid and newer-version files | PASS | g01–g12 |
| Result dialog focus (D8) and stale count (D9) | FAIL→FIXED | g08, g15, g19 |
| Parental PIN needed to restore playlists; restore never weakens parental flags | PASS | g13–g15 |
| Restore of a deleted playlist, including logins and favorites | PASS | g16–g21 |
| System picker (another location) | BLOCKED | The box has no document picker; AUTO only |

## 3. Defects found in this pass

| ID | Severity | Summary | Status |
|---|---|---|---|
| D1 | Low | Network error wording | Fixed, re-tested |
| D2 | Low | Choosers did not mark the current value | Fixed, re-tested |
| D3 | Low | Focus after the player | Fixed, re-tested |
| D4 | High | Two players after PiP (broke the 1-connection limit) | Fixed, re-tested |
| D5 | Low | External player stub | Fixed, re-tested |
| D6 | Low | Guide-for-group focus | Fixed, re-tested |
| D7 | Low | Focus lost after the last favorite was removed | Fixed, re-tested |
| D8 | Low | Backup result dialog focus | Fixed, re-tested |
| D9 | Low | Stale backup count | Fixed, re-tested |
| D10 | Low | Search hint missing | Fixed, re-tested |
| D11 | Low | Enter did not focus search results | Fixed, re-tested |
| D12 | Medium | No next/previous episode | Fixed, re-tested |
| D13 | Medium | Failed new playlist became active | Fixed, re-tested |
| D14 | Low | Slow timeout for unreachable servers | Open, documented |
| D15 | Low | Search hint shown with a kept query | Fixed, re-tested |
| D16 | High | External player bypassed the parental lock | Fixed, re-tested |
| D17 | Low | Focus jumped from list ends into the rail | Fixed, re-tested |
| D18 | Low | Invert setting summary was misleading | Fixed, re-tested |
| D19 | Medium | Watching live, HOME, then reopening from Projectivity (Shortcut Maker tile) showed the main screen instead of the last channel: its launch intent differs from the task's root intent, so `singleTask` cleared the player and "Start with last channel" only ran on cold start | Fixed in 0.1.1: MainActivity.onNewIntent restarts the last live channel when the player was left with HOME (setting on, parental lock still enforced). Re-tested on onn 4K Streaming Box <lan-device>: real Projectivity tile, Projectivity-style and leanback relaunch, relaunch after background process kill all resume with audio; BACK then relaunch stays on the main screen; no crashes |

Regression guards are in `app/src/test/java/com/youriptv/app/QaRegressionTest.kt`. The last build
ran 103 unit tests with 0 failures.

## 4. Remaining manual checks
1. Device reboot: restart the box, open YourIPTV, and confirm that the parental and Settings locks
   persist.
2. Physical remote: GUIDE (Google TV takes this key), CH± if present, and long-press OK on a guide
   programme.
3. Backup "another location": needs a device that has a document picker.
