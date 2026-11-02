# Known issues and test limitations

- "Prefer software video decoding" is CPU-limited on the moto g 2025 at 1080p60. Presentation
  alternates between 60 fps and about 9 fps. The default hardware decoder plays a steady 60 fps.
- On a phone, relaunching through a plain launcher intent (`adb shell monkey`) while in
  landscape full screen recreates the main screen at the categories level without playback. The
  real launcher icon and Recents resume full screen correctly.

- Google TV intercepts the remote's dedicated GUIDE key (re-confirmed on 0.3.0: it opens the
  Google TV home screen). Open the YourIPTV guide from TV in the main menu or from player options →
  TV guide instead; avoiding system privileges is intentional.
- 0.3.0 Live TV levels differ from TiviMate in these details: the main menu is hidden completely
  in the categories and guide (TiviMate keeps a narrow icon strip), the main menu level fits the
  guide beside the menu (with hourly time labels) rather than sliding it further, long-press Back does not jump back to the player, and Back on the
  main menu keeps YourIPTV's "press BACK again to exit".
- A selected locked category asks for the parental PIN, and each channel preview in it asks again
  (unchanged session protection from 0.2.0).
- The test provider periodically ends live streams (observed about every 1–5 minutes while idle).
  The preview then shows "Loading preview…" for about 3 seconds while it reconnects. This
  happens without any navigation.
- Catch-up archive host and ISP filtering: on the test network the ISP (a "Security Shield" filter)
  replaces the provider's archive host with an HTML block page. Catch-up works only when the device
  reaches that host another way, for example through a VPN app on the device. Archive playback was
  verified on the TV box with its VPN on. YourIPTV needs no VPN and has no VPN feature. When an
  HTML page arrives instead of media, the player reports a network filter/block, not a provider
  or app fault.
- Archive streams start where the provider's archive starts. This can include a few minutes of the
  previous programme, for example the end credits of an overrunning show.
- Android TV puts the box to sleep when the TV switches off over HDMI-CEC ("Going to sleep due to
  hdmi"), which stops playback. This is system behaviour; YourIPTV keeps the screen on while playing.
- EPG coverage depends on provider `tvg-id`/EPG IDs; unmapped channels show no programme data.
- The provider catalogue contains `##### … #####` separator entries that are not useful streams.
  Some report a playback error; others can keep buffering. A slow preview shows a hint after
  20 consecutive seconds in the buffering state, remains navigable, and continues trying rather
  than assuming a provider failure.
- History rows recorded before the DB v2 upgrade carry no stored metadata. Entries that are no
  longer in the catalogue are therefore not shown.
- Temporary test PINs and restrictions were removed after the 0.2.0 validation; the test box was
  restored to its original no-playlist, no-recording, unlocked setup.
- There is no in-app PIN recovery. A forgotten parental or Settings PIN can only be reset by clearing
  YourIPTV's data in Android settings, which also removes playlists, so it is not a silent bypass.
- Wrong PINs are throttled per PIN: after 5 failures the prompt is blocked for 30 s, doubling per
  further failure (max 15 min). The counter survives app restarts.
- Device reboot persistence of the locks was not tested (wireless ADB on the test box does not
  survive a reboot); persistence across force-stop and APK update was verified.
- Phone layouts are not device-tested (TV-box-only scope).
- Audio delay applies to decoded (PCM) audio only; with HDMI passthrough/bitstream audio it has no effect.
- Reminders and scheduled recordings run only while YourIPTV is running. By design the app adds no
  background service, alarm, notification or boot permissions; a reminder or recording that falls due
  while the app is closed is not triggered.
- A recording uses one provider connection. The authorized provider allows one connection, so
  watching another channel while recording fails with the provider's limit (the error message notes
  the active recording). This is a provider limit, not an app restriction. An M3U link generated
  by an Xtream panel (`get.php?username=…`) inherits the limit reported by a saved Xtream playlist
  for the same server and user; other M3U playlists declare no limit, so no warning is shown.
- If YourIPTV is closed or reinstalled mid-recording, the partial file is kept and marked
  "YourIPTV was closed while recording."
- Recording sizes in the Recordings list refresh when the section is reopened or a recording
  finishes, not continuously.
- Recording Stop/finalization/Delete worked through touch options in final 0.2.0 testing.
  Exact D-pad focus restoration is not fully verified: intermittent returns to Google TV Home
  occurred without a new app crash/ANR record. Repeat with the physical remote untouched and
  collect input/lifecycle logs if this recurs.
- Auto frame rate: the Onn box and test display expose only 1920×1080 @ 59.94 Hz, so no refresh-rate
  switch can be observed; frame-rate detection was verified via log only.
- The authorized M3U declares no catch-up attributes. For M3U links generated by an Xtream panel,
  YourIPTV reads archive availability (`tv_archive`/`tv_archive_duration`) from the same panel when
  the playlist is refreshed. Plain M3U catch-up (`catchup*` attributes) is unit-tested only.
- Deferred: Stalker portal (no test portal) and multiview (the authorized provider allows one connection).
- Adding a playlist whose server cannot be reached at all (no route) can take about 2–3 minutes to
  report "loading failed". The progress status stays visible, and the active playlist is not changed.
- Backup files are stored in the app's own folder (`Android/data/com.youriptv.app/files/backups`).
  Uninstalling YourIPTV deletes them, so copy a backup elsewhere before uninstalling. Saving to or
  opening from "another location" appears only on devices with a system document picker; the onn
  box has none, so that path is covered only by automated tests.
- Backup files contain playlist logins, never PINs. Treat them as private.
- The on-screen Previous/Next buttons on the player controller cannot be reached with the D-pad.
  Use the player menu ("Next episode"/"Previous episode") or media keys.
- The "Locked groups" list shows every group in the playlist (101 for the test M3U). Reaching
  SAVE needs many DOWN presses; Back cancels without changes.
- On 29 Oct (an older build) the box logged one Android framework crash inside accessibility-focus
  handling (`ViewRootImpl.getAccessibilityFocusedRect`), with no YourIPTV code in the stack. It
  coincided with automated UI dumps and did not recur during the full QA pass.