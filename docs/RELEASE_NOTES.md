# Release notes

## 1.0.5 (version code 36)

- Manage playlists from Settings without another Settings PIN during the same protected visit.
- Use Delete playlist to choose a playlist and confirm its removal.
- Show or hide the playlist password; it starts masked and is hidden again when leaving.
- Keep parental playlist protection, the YourIPTV login shortcut and all playback features free.

Thanks to Dylan B. for the suggestions for the current build.

Settings → Playlists → Manage playlists and the editor share an authenticated in-memory visit.
Validated one-use navigation handoffs preserve that visit only for the intended internal target.
Direct entry still needs authorization, and leaving/backgrounding revokes it. Independent parental
playlist protection is preserved; the always-on Settings policy and master/custom rules remain.

The visible Delete playlist action opens a chooser and named confirmation with Cancel as the safe
default. Context-menu deletion uses the same authorization and cleanup. Refreshing blocks deletion,
and a stale/missing target cannot delete another playlist. Existing catalog/guide/favorites/history/
reminder/pending-recording cleanup remains; finished recording files are retained with stored stream
URLs cleared. Other playlists remain intact. Deleting the active playlist selects a remaining one
or shows the empty state.

Show password / Hide password preserves exact text and cursor position. The field starts masked,
remasks on leaving/background or changing setup mode, and never restores revealed state. The
1.0.4 two-field YourIPTV shortcut and normal M3U/Xtream setup remain. No schema migration,
credentials, permissions, player feature locks or playback changes are added.

Based on clean 1.0.4/code 35. Fresh final verification passed **324 tests in 44 classes** with
zero failures/errors/skips, signed release assembly/vital lint, native/compiled APK checks,
15 archived updater checks, identity audits on 21 retained APKs and three isolated fixtures
exercising actual playlist-deletion SQL. The initial suite's obsolete Main routing assertion
was corrected to verify the manager's parental gate; failed reports are preserved separately.

The exact signed APK passed scoped onn. 4K Pro/API 34 checks after an in-place code 34→36 update,
with installed bytes/certificate verified: one Settings PIN across manager/editor/Back, Home
relock, direct-entry PIN, visible Delete chooser/confirmation with Cancel, password Show/Hide/
remask, existing Xtream account/catalog/guide refresh, live picture, public About and populated
Movies/Shows browsing. No real owner playlist was deleted and synthetic password input was not
saved. The YourIPTV shortcut's two fields/blank validation were checked without creating an account.
Phone, fresh parental-configuration changes, VOD/Series playback and actual automatic download/
install/success-popup tests were not performed. Historical release passes do not fill those gaps.

Prepare a complete signed APK for direct upgrades from compatible official releases; predecessor
code 35 controls optional/required prompts, not a minimum installed version. Retired updater/
installer exceptions remain documented. Local development commit/tag and Repository/Desktop/
backups preparation follow verification; actual promotion belongs in publication notes/final audit.
No upload, deployment, GitHub call/publication, key change, rebranding/companion or buffer work
was performed.

See [Settings security](SETTINGS_SECURITY.md) and [QA evidence](QA_CHECKLIST.md).

## 1.0.4 (version code 35)

- Add a YourIPTV playlist option after M3U and Xtream Codes.
- Sign in to YourIPTV with only your provider username and password.
- Use the preset YourIPTV server and playlist name while keeping normal Xtream editing.
- Keep all playback features free and preserve existing playlists, Settings and parental protections.

Thanks to Dylan B. for the suggestions for the current build.

The shortcut uses the fixed server **http://cw-25.com**, playlist name **YourIPTV**, the existing
Xtream source type, provider guide and default User-Agent. It asks for no server, playlist name,
guide URL or User-Agent. Stale hidden values from other setup choices cannot redirect it.
An authorized working provider account is still required; no credentials, content or account
are included. A saved shortcut connection reopens as a normal full Xtream editor behind existing
Settings and applicable parental authorization. Existing playlists are not converted or renamed;
there is no database or backup-format migration. Existing M3U/generic Xtream setup and player
features remain available.

Based on clean 1.0.3/code 34. Fresh 1.0.4 verification passed **300 tests in 41 classes** with
zero failures/errors/skips, signed release assembly/vital lint, native/compiled exact-APK checks,
14 archived updater checks and identity audits on 20 retained APKs. The initial suite had one
branding-centralization failure; it was corrected before the fresh complete pass, and failed
reports remain preserved separately. The official package/signing identity, seven permissions,
update routes, public About, Settings/parental restrictions and provider limits remain.

The owner restricted physical testing to 192.168.1.150. All three TV connection attempts,
including an owner-ready retry, failed with Windows socket error 10013; independent TCP
connection also failed. No 1.0.4 device installation, live shortcut login, navigation/playback,
automatic download/install or successful-update popup was tested. Phone tests were not performed
per owner direction. Historical 1.0.3 device passes and new offline/compiled checks do not replace
those missing physical checks.

Prepare a complete APK for direct upgrades from compatible official installations; no
intermediate release is required. Predecessor code 34 controls optional/required prompts only,
not a minimum installed version. Retired update addresses and older missing installation
support remain manual in-place exceptions. Authorized scope remains local preparation:
development commit/tag and Repository/Desktop/backups checks follow verification and are
recorded in the publication instructions/final audit. Uploads, GitHub calls/publication, server
deployment and key changes are excluded. Rebranding/companion and adjustable buffering stay paused.

See [QA evidence](QA_CHECKLIST.md).

## 1.0.3 (version code 34)

- Fit complete movie and show posters and captions on short phone screens.
- Use a compact browsing header on short phone screens while retaining the full TV layout.
- Restore the selected series episode and focus after playback with touch or remote controls.
- Keep playback features free, subtitles off by default, and existing Settings and parental protections.

Thanks to Dylan B. for the suggestions for the current build.

Compatible phone layout and Series-return focus corrections based on clean 1.0.2 / code 33.
An earlier private candidate passed 282 tests in 40 classes but failed Series return focus;
its evidence is preserved separately. The corrected final code passed **288 tests in 40 classes**
with zero failures/errors/skips. Signed release assembly and release vital lint passed in 3m6s;
13 archived updater checks and identity audits of 19 retained APKs passed. The exact final APK
passed scoped Samsung SM-S166V/API 36 and onn. 4K Pro/API 34 checks, including plain/touch-menu
Series returns to the selected E3 and subsequent remote Right to E4. Phone movie video/resume,
normal/enlarged text, TV layout and Settings/About checks passed. A real automatic download/install
and successful-update popup were not device-tested; the four-note receipt/credit has JVM and DEX
coverage. Post-commit artifact/Repository/Desktop results belong in the publication notes/final audit.
The official package/signing identity, update routes and requested permissions are retained.
The complete APK supports direct upgrades from compatible official installations; no intermediate
release is required. Planned predecessor code 33 controls optional/required prompts only.
This work is authorized locally only: no 1.0.3 server upload or GitHub publication is included.

See [phone/VOD behavior](VOD_NAVIGATION.md) and [QA evidence](QA_CHECKLIST.md).

## 1.0.2 (version code 33)

Later phone follow-up: the Samsung SM-S166V displayed five columns with clipped poster/caption
cards on the short landscape Movies/Shows grid. Live video and movie details/buttons/favorites
worked, but movie video/resume was not confirmed on that phone. The original verification below
predates that session; 1.0.3 addresses the grid limitation and requires new checks.

- Larger movie and show posters for easier browsing.
- Movie details before playback.
- Browse seasons up/down and episodes left/right, with your selection remembered.
- Provider episode pictures, with series artwork when episode pictures are unavailable.
- Improved artwork loading while browsing large libraries.
- About remains accessible without a PIN. Its duplicate entry inside locked Settings has been removed.
- All player features remain free. Settings and parental protections remain enabled.
- Subtitles start off by default; you can turn them on in the player.

Thanks to Dylan B. for the suggestions for the current build.

This release is based on clean 1.0.1 / code 32. The full offline suite passed 275 tests in 40 classes,
with zero failures/errors/skips. Signed release assembly, release vital lint, native APK checks,
12 archived updater source checks and identity checks on 18 retained APKs passed. Scoped onn. 4K Pro
checks passed on the exact installed APK for live TV/guide, VOD navigation/playback/resume,
episode subtitles and Settings/About. The backup website serves the exact verified APK and
metadata, deployed over certificate-verified FTPS/TLS 1.3 with independent HTTPS readback.
Post-commit source/Repository/Desktop checks are recorded in the local publication instructions
and final audit; GitHub publication remains manual.
Physical-phone, process-recreation, failure-injection and live automatic-update checks were not
performed. The successful-update popup has automated coverage only. See the QA checklist for scope.
Compatible official installations can upgrade directly without intermediate versions. The
predecessor code controls required versus optional updates, not the minimum installed version.
Retired update addresses and older missing installation support require a manual in-place update.

See [Movies/Shows navigation](VOD_NAVIGATION.md) and [QA status](QA_CHECKLIST.md).

## 1.0.1 (version code 32)

- Keep Settings protection always enabled on new and existing installations, including installations
  where it was previously off. The permanent Settings master cannot be changed or removed.
- Preserve existing custom Settings PINs as another way to open Settings. Require the master to
  add, change or remove a custom Settings PIN. Removing a custom PIN leaves master protection on.
  Master and custom attempts share persistent Settings lockouts; neither bypasses a lockout.
- End legacy shared-parental access to Settings. Parental PINs, restrictions and failure counters
  remain independent and unchanged; the Settings master never overrides them.
- Make About directly available from the main menu on phones and TV without a Settings PIN.
  Opening About does not unlock Settings. Version information, free-use notices and credits remain.
- Allow editing existing playlist connections after Settings authentication, with any additional
  parental playlist lock still enforced. Check authorization before displaying connection details
  and saving changes; the authorization ends when the protected visit ends.
- Keep playback, package identity, official signing identity, requested permissions and update
  addresses unchanged. All player features remain free. Buffer and companion rebranding work stay paused.
- Local release preparation only. No 1.0.1 publication, website deployment or device testing is
  implied. A fixed shared master can be learned and shared; it is not owner-specific authentication
  or an anti-copy mechanism. See [Settings security and migration](SETTINGS_SECURITY.md).

## 1.0.0 (version code 31)

- Stable version-only promotion of the owner-accepted clean 0.5.6 baseline. No new features or
  playback fixes; known occasional stream interruptions remain possible.
- Keep the official package/signing identity, permissions, settings and updater behavior.
  All player features remain free. Buffer changes and companion rebranding are not included.
- Local release preparation only; publication is separate. A higher-version in-app installation
  has not yet been tested for this release.

## 0.5.6 (version code 30)

- Keep GitHub as the primary update source and check `https://youriptv.us/updates/update.json`
  if GitHub's update metadata is unavailable or invalid.
- Retry an unavailable GitHub APK from `https://youriptv.us/updates/` using the same release's
  versioned filename. Both hosts must serve identical APK bytes; size, SHA-256, package, version,
  signing certificate, app label, Android compatibility and requested-permission checks still apply.
- Preserve a newer cached release if either server is stale. A same-version response cannot
  replace the cached version name, size, SHA-256 or required-update threshold.
- Keep playlists, settings and the official package/signing key during in-place
  updates. No new permissions or changes to player features.
- Add website manifest generation with `scripts/make_update_json.py --mirror` and backup-server
  publishing instructions in `docs/UPDATE_SERVER.md`. Existing apps need to receive 0.5.6 through
  GitHub or a technician install before website fallback is available.

## 0.5.4 (version code 28)

- Version-only release; player and playlist features are unchanged from 0.5.3, including hold Back
  to return to playback and the installed-version changelog after in-app updates.
- Package ID, official signing key, requested permissions, settings and production update URL are
  unchanged. Compatible earlier versions can update directly; installing each intervening version
  is not required.
- Once published with previous version code 27, devices on 0.5.3 can Install or Ignore; devices on
  version code 26 or below must update.

## 0.5.3 (version code 27)

- Hold Back in the app or TV guide to return to the current stream in full screen. Short Back presses
  keep their usual action. Existing playback is reused; a released live stream is reopened only after
  validating the channel and current parental restrictions.
- After an in-app update, show the installed version and its bundled changelog. The confirmation stays
  pending until acknowledged with Got it, including after the app is closed or restarted.
- Devices on 0.5.2 can Install or Ignore; devices on version code 25 or below must update.

## 0.5.2 (version code 26)

- Disable editing existing playlists while Settings lock is on, including during a temporary Settings
  unlock. Turn off Settings lock to edit; adding playlists and refreshing channels or EPG still work.
- Devices on 0.5.1 can Install or Ignore; devices on version code 24 or below must update.

## 0.5.1 (version code 25)

- **Automatic updates are On by default** and no longer depend on any other setting: no Settings PIN
  or technician permission is needed to turn them on or for updates to install.
- Boxes upgrading from 0.5.0 or older have Automatic updates switched On once, even if they were Off.
- Without the technician's "Install unknown apps" permission, Android shows its own prompt during an
  update instead of the update failing. With the permission, Android 12+ still installs silently.
- Package ID, official signing key, requested permissions and production update URL are unchanged.
  Devices on 0.5.0 can Install or Ignore; devices on version code 23 or below must update.
## 0.5.0 (version code 24)

- Version-only maintenance release; no new player or playlist features since 0.4.3.
- Package ID, official signing key, requested permissions, settings, and production update URL are
  unchanged.
- Once published, devices using the current updater on 0.4.3 can Install or Ignore; devices on
  version code 22 or below must update.

## 0.4.3 (version code 23)

- Add direct **Edit current** shortcuts in Playlists and Settings for the active provider's M3U or
  Xtream URL and credentials, plus playlist name, EPG URL, and user agent.
- Respect the playlist parental lock in Settings, and report when a playlist disappears or cannot
  be saved instead of silently turning an edit into an add.
- Keep playlist and editor action buttons reachable on narrow screens with horizontal scrolling.

## 0.4.2 (version code 22)

- Version-only release for testing automatic updates from 0.4.1 through the existing GitHub updater.
- Player functionality, branding, package ID, signing key, permissions and settings are unchanged.
- Devices on 0.4.1 can Install or Ignore; ignored updates are offered again at the next app opening.
- Publish as a normal Latest release, not a pre-release. It will be offered to all eligible devices.

## 0.4.1 (version code 21)

- Update source moved to the GitHub repository `youriptvus/YourIPTV` (the previous repository is
  retired). Boxes read `https://github.com/youriptvus/YourIPTV/releases/latest/download/update.json`.
- 0.4.0 boxes cannot find this release by themselves (they still look at the retired repository),
  so install 0.4.1 once with `adb install -r`. The permission set is unchanged, so the technician
  grants and all data are kept.
- Add the official website, `https://youriptv.us`, to the README and default About screen.

## 0.4.0 (version code 20)

- Technician-enabled automatic updates for customer boxes (Settings → App Updates, behind the
  Settings PIN). The technician allows update installs and automatic restart once, then turns
  Automatic updates On.
- Each app opening reads `update.json` from the latest GitHub release. Customers choose Install or
  Ignore; the offer repeats every opening until current. A box 2+ releases behind must update
  (Install only, also enforced offline from the cached info).
- Silent in-place install on Android 12+ with download/install progress, automatic app restart
  (Android ≤ 14 with the technician's restart permission) and an "Update failed" screen with Retry.
  No permission prompts or Android settings for customers; data and grants are kept.
- Downloads are checked for host, size, SHA-256, package, signer, version, label and an identical
  permission set. `scripts/make_update_json.py` generates `update.json` for each release.
- New permissions: `REQUEST_INSTALL_PACKAGES`, `UPDATE_PACKAGES_WITHOUT_USER_ACTION`,
  `SYSTEM_ALERT_WINDOW`. Upgrading from 0.3.9 therefore needs one technician install.
  Package ID and signing key unchanged; all player features remain free.

## 0.3.9 (version code 13)

- Branding centralization (prep for a separate, authorized rebranding tool — the only paid offering;
  all player features stay free): all brand surfaces now resolve from one place —
  `res/values/branding.xml` (name + company/contact strings), the launcher mipmaps, the TV `banner`
  drawable, and the `ui/Branding` object (About-screen composition). YourIPTV remains the default
  branding. Branding is read-only and separate from player behavior, settings, playlists, credentials,
  and security controls. The About screen always keeps the free-for-everyone notice and the
  open-source license acknowledgements. Integration points and which changes require rebuild/re-sign
  are documented in `docs/BRANDING.md`. No payment UI or branding controls were added to the app.
- Brand-neutral screen text: the About title and body, Settings → About entry, welcome text and the
  free-use notice (About, welcome, playlist editor) now read the app name from `@string/app_name`
  instead of hardcoding "YourIPTV". Guarded by the new `BrandingCentralizationTest`.
- Anti-rebranding hardening, layered on the existing signing-certificate check (0.3.5), which is the
  real anchor: any change to the installed APK's code, resources or icon forces a re-sign, which the
  certificate check already rejects before the app starts.
  - Package lock: release builds refuse to run under any application ID except `com.youriptv.app`,
    with a single reserved exception seam (`BrandIntegrity.CUSTOM_TOOL_EXCEPTION`, closed by default)
    for a first-party tool to be added later.
  - Name lock: release builds refuse to run if the app display name was changed from "YourIPTV".
  - A modified copy shows the existing integrity screen with a "this copy has been modified" message.
  - Advisory root / ADB / debugger signals are logged (not fatal), so rooted or developer devices
    keep working; they can be made strict for a specific threat model.
  - Optional server-defined branding (`BrandAssets`): when an HTTPS endpoint is configured, the name
    and logo come from your server, so swapping files in res/ does not change what users see. Off by
    default; no network contact until configured, and no new dependency.
  - A dedicated `proguard-rules.pro` keeps the integrity classes intact under R8.
- Not included by design: runtime self-hashing of classes.dex. R8 rewrites the dex on every build, so
  a hardcoded dex hash is brittle and adds nothing over the signing-certificate check.

## 0.3.8 (version code 12)

- Fix: a live channel that keeps dropping every minute or so no longer waits 30 seconds between
  reconnects. A reopen that plays for 10 seconds now counts as successful (previously a full
  minute), so the next drop gets the quick reconnects again. The 30-second retry pace is kept
  only for reopens that never start playing.
- Fix: the Live TV guide preview gave up after two failed reconnects ("Preview unavailable …
  Choose another channel") and stayed stopped. Seen on the onn. box when the provider refused one
  reopen (HTTP error) right after ending the stream. The preview now uses the same recovery as
  full screen: quick reconnects, then a new attempt every 30 seconds until the channel plays.
- The log now records why each live reconnect happened (player error code, stream ended, or 30
  seconds of buffering) under the `YourIPTV-Recovery` tag.

## 0.3.6 (version code 10)

- Fix: a live channel (seen on Bizarre Foods) that played well for a long time, buffered a few
  times and then stayed stuck buffering without ever reloading. Two causes in 0.3.4:
  - nothing watched for a stream stuck in the buffering state, so a source that stopped
    delivering data was never reopened;
  - retry counters reset whenever playback became ready for a moment, and retries only prepared
    the same exhausted source again instead of reopening it.
- Live preview and fullscreen now share one recovery coordinator. After 30 seconds of continuous
  buffering, the stream is reopened from a fresh media source on the same player, closing the old
  connection first. Errors, ended streams and "behind live window" use the same coordinator, so
  only one reconnect is pending at a time (important with a one-connection provider).
- Up to three quick reopens are made. If those fail, the connection is released and the stream is
  reopened every 30 seconds for as long as you stay on the channel, with an on-screen notice
  (OK retries immediately). The count resets after a full minute of uninterrupted playback.
  Playback recovers by itself as soon as the stream is available again.
- Short buffering does not trigger a reopen. Buffer size, start-up speed and decoder settings are
  unchanged. Movies, Series and catch-up keep their existing retry behavior.

## 0.3.5 (version code 9)

- Release builds check the installed app's signing certificate against the official YourIPTV
  certificate before initializing the app. A copy signed with an unrecognized key shows an
  explanatory screen instead of starting. Debug builds remain usable with a developer's debug key.
- Release code is obfuscated with Android's R8 optimizer. No account, payment, additional service,
  or network connection is required by these checks.
- These protections deter straightforward repackaging; they do not encrypt the branding or make
  modification impossible. Someone who changes or removes the checks, including in shared source
  code, may still produce a modified copy.

## 0.3.4 (version code 8)

- Fix Back navigation getting stuck after returning from live fullscreen playback on phones.
  Back now uses the current Live TV navigation level rather than relying on focus transfers,
  allowing Back to continue from guide to categories to main menu even in Android touch mode.
  Portrait also tracks these navigation levels without changing its layout or tap behavior.
- Playback, stream parsing, category ordering and Movies/Shows navigation are unchanged.

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
