# YourIPTV

YourIPTV is an independent IPTV player for **Android TV** and **Android phones**, written in Kotlin
with [Media3 ExoPlayer](https://developer.android.com/media/media3/exoplayer). You add your own
**Xtream Codes** or **M3U / M3U Plus** playlist and an **XMLTV (EPG)** guide from a provider you
are authorized to use. The **YourIPTV** setup shortcut accepts your existing provider username
and password for its preset server; it does not supply an account, channels or subscription.

YourIPTV is an app only. It is not an Android launcher, a home-screen replacement, an OS
modification, or a device-management tool. It works the same whether it is opened directly or
from a separate launcher.

Website: [youriptv.us](https://youriptv.us)

## Download and install

**[Download the latest YourIPTV APK](https://github.com/youriptvus/YourIPTV/releases/latest/download/YourIPTV.apk)**

You can also open the [latest release page](https://github.com/youriptvus/YourIPTV/releases/latest)
and choose **YourIPTV.apk** under **Assets**. These links use the release marked Latest on GitHub;
local source changes do not become available there until the owner publishes the matching release.
YourIPTV supports Android phones and Android TV running Android 6.0 or newer.

### Android phone or tablet

1. Open the download link on your Android device and download **YourIPTV.apk**.
2. Open the file from your browser's downloads or the device's **Files / Downloads** app.
3. If Android asks, allow **Install unknown apps** for the browser or file manager opening the APK,
   then return to the installer. Wording varies by Android version. Follow Android's prompts;
   a managed device may prevent installation.
4. Choose **Install**, then **Open**. Add your authorized provider playlist using **M3U**, **Xtream
   Codes**, or the two-field **YourIPTV** shortcut described below. No provider account is included.
   Keep your login details inside the app.

### Android TV or Google TV

1. Download **YourIPTV.apk** using a browser on the TV, or download it on a phone/PC and copy it
   to a USB drive or transfer it to the TV. Open the APK using the TV's file manager.
2. If prompted, allow that browser or file manager to **Install unknown apps** in Android Settings.
   Return to the APK and choose **Install**. Android's device restrictions still apply.
3. Open **YourIPTV** from the TV's app list and add your authorized provider playlist.

### Updating an existing installation

With **Automatic updates** enabled, YourIPTV checks for updates when opened and offers the
available update. Android may require installation permission or confirmation. To update manually,
download and open the latest APK, then accept Android's update/install prompt. **Do not uninstall
YourIPTV or clear its data**: a compatible in-place update keeps playlists and local app data.
The 1.0.1 update deliberately turns Settings protection on for all installations; read the
[Settings migration details](docs/SETTINGS_SECURITY.md).

If Android reports a signing conflict or refuses an older version, keep the existing app and its
data and contact the maintainer. Do not uninstall it to force the update. See the
[upgrade guide](docs/UPGRADING_TO_1_0.md) for older-version exceptions and the technician setup below
for the app's automatic-update permissions.

## Release status

| | |
|---|---|
| Current project version | **1.0.5** (`versionCode` 36), application ID `com.youriptv.app` |
| Stage | Signed APK, automated checks and scoped TV verification passed; local publication preparation |
| Distribution | Side-loaded APK via GitHub Releases, with a website update mirror. No Google Play listing |
| Supported Android | `minSdk` 23 (Android 6.0) to `targetSdk` 36 |
| Device checks | Scoped onn. 4K Pro/API 34 checks on 192.168.1.150 passed; phone testing excluded by owner direction |

1.0.5 keeps one protected Settings visit across **Settings → Playlists → Manage playlists**
and the editor, so an authorized internal move does not ask for the Settings PIN again.
Leaving that flow or backgrounding revokes access. The independent parental playlist PIN still
applies. A visible **Delete playlist** action opens a chooser and confirmation; Cancel is the safe
default. **Show password / Hide password** reveals only the current editor field, which starts
masked and is masked again when leaving or changing setup mode. See the
[Settings authorization policy](docs/SETTINGS_SECURITY.md) and provider instructions below.

The clean 1.0.4 baseline retains the two-field **YourIPTV** adding shortcut for **http://cw-25.com**,
ordinary Xtream storage/editing, and the 1.0.3 Movies/Shows corrections. Existing playlists are
not renamed or migrated. Earlier **300 tests / 41 classes**, **14 updater checks** and **20 APK
identity audits** belong to 1.0.4; that release had no physical device test. Earlier 1.0.3 phone/TV
passes are also historical. They do not certify 1.0.5. The [QA checklist](docs/QA_CHECKLIST.md)
separates the fresh results below from those records.

The final 1.0.5 code passed **324 tests in 44 classes** with no failures/errors/skips, signed
release assembly/vital lint, **15 archived updater checks**, **21 retained-APK identity audits**
and three isolated fixtures exercising the actual playlist-deletion SQL. On the exact APK,
**23 scoped TV checks** passed: one-PIN Settings→manager/editor/Back navigation, Home relock, direct-entry PIN checks,
visible deletion confirmation/Cancel, password visibility/remasking, a real existing-account
catalog/guide refresh, live picture, public About and populated Movies/Shows browsing. The TV
updated in place from code 34 to code 36 and its pulled APK matched the verified signed bytes.
Normal restart retained the last channel, and final live frames still moved after management.
The observed post-install AndroidRuntime log had zero app fatal blocks; this does not certify
native-crash/ANR absence or long-duration stability.
No real owner playlist was deleted, no new shortcut account was submitted, and no fresh parental
configuration, phone, VOD/Series playback or actual automatic-installation/popup test is claimed.

The official package/signing identity, requested permissions and update routes are retained.
Settings protection stays on; existing custom PINs remain usable, and only the fixed master
manages them. About remains public from the main menu. Parental restrictions, provider limits,
subtitles-Off defaults/manual controls and all free player features remain. The shared Settings
master is discoverable and is not a private owner identity or anti-copy protection. Read
[Settings security and migration](docs/SETTINGS_SECURITY.md). Buffer and companion rebranding
work remain paused and excluded, with no reserved future release version.

The bundled 1.0.5 installed-update confirmation contains four release-note bullets and the credit:

**Thanks to Dylan B. for the suggestions for the current build.**

The last recorded website mirror serves **1.0.2 / code 33**. This 1.0.5 work is authorized locally
only; it does not include server uploads or GitHub publication. Preserve existing release assets.
GitHub publication remains manual and workspace tools do not call GitHub. Record actual 1.0.5
verification in the QA record. Post-commit source/archive/Repository/Desktop checks are recorded
in `D:\APK\backups\YourIPTV-1.0.5-PUBLISH.txt` and the verification folder's `final-audit.json`;
those records, rather than this pre-staging source text, establish local promotion status.

Prepare 1.0.5 as a complete APK that can update directly from every compatible official
installation, without intermediate releases. The prepared `previousVersionCode: 35` is only the
optional/required prompt threshold, not a minimum installed version. Retired updater addresses
and missing installation support remain explicit manual in-place upgrade exceptions. See the
[upgrade guide](docs/UPGRADING_TO_1_0.md).

The signed APK on each GitHub Release uses the fixed asset name `YourIPTV.apk`. The latest
release is available at
[the stable APK URL](https://github.com/youriptvus/YourIPTV/releases/latest/download/YourIPTV.apk),
which is the primary distribution address for the in-app updater. Starting with 0.5.6, the updater
can use [youriptv.us](https://youriptv.us/updates/update.json) when GitHub fails. See the technician
setup below and [backup-server setup](docs/UPDATE_SERVER.md).

### Customer-box automatic updates (0.4.0 and newer)

**Technician setup (once per box, at install time):**

1. Install the current version with the official release key (`adb install -r` or a file manager).
   Never uninstall or clear data. Boxes on 0.4.0 need a one-time technician install of the current release because
   the update source moved to `youriptvus/YourIPTV`; older versions also need a technician install
   because 0.4.0 added updater permissions.
2. **Automatic updates** are **On by default**. Normal update checks and installation do not ask
   for the Settings PIN. Changing their configuration requires opening protected Settings.
   For the smoothest updates, authenticate and open **Settings → App Updates** once:
   - **Allow update installs (technician)** opens Android's *Install unknown apps* page; allow YourIPTV.
     (Google TV shows the full app list; scroll to YourIPTV.) Without it, Android asks once during
     the first update to allow installs from YourIPTV.
   - **Allow automatic restart after updates (technician)** opens *Display over other apps*; allow
     YourIPTV. This only lets the app reopen itself after an update (Android 10–14).
   - Settings protection is always enabled from 1.0.1 onward. Keep the master with the responsible
     technician; an optional custom Settings PIN can also authorize Settings access.

**Customer experience:**

- Every time the app is opened it reads `update.json` from the latest GitHub release. Starting with
  0.5.6, unavailable or invalid GitHub metadata triggers a check of
  `https://youriptv.us/updates/update.json`. If a newer
  version exists the customer sees **Install** or **Ignore**. Ignore is not remembered: the offer
  returns at every opening until the app is current.
- Once the box is **2 or more releases behind** (installed `versionCode` < `previousVersionCode`), the
  update is required: a full screen offers only **Install update**; Back closes the app. Accepted
  metadata is cached, so this is still enforced when both update servers are unreachable. An older
  server response cannot replace a newer cached release, and a same-version response cannot
  replace its version name, size, SHA-256 or required-update threshold.
- Compatible earlier versions can update directly to the latest release; installing each intervening
  version is not required. Official 0.4.1 and later releases using the current GitHub update address
  can update directly to the complete compatible 1.0.5 / code 36 release once verified and available;
  0.5.2 and 0.5.3 need no intermediate install.
  Apps older than 0.5.6 need that first update through GitHub or a technician install before they
  can use the website fallback.
- Install shows *Downloading update… x%* and *Installing update… x%*. On Android 12+ the install
  is silent (no Android prompt). The app then closes briefly and restarts by itself. Failures show
  **Update failed** plus the reason, with Retry (and Close when not required). After a restart a
  confirmation shows the installed version and its changelog. Choose **Got it** to acknowledge it;
  the confirmation remains pending if the app is closed before acknowledgement.
- When the technician allowed update installs, customers never see permission prompts. Playlists,
  credentials, existing custom PINs and permission grants are kept (in-place update). In 1.0.1,
  Settings protection becomes enabled even if it was previously off; parental protection is unchanged.

**Limits:**

- Android 11 and older show one Android *Install* confirmation per update.
- Android 15+ does not let a closed app reopen itself; the customer opens the app again from the home
  screen (the app tells them so before installing).
- Without the install permission, Android shows its own prompt to allow installs from YourIPTV during
  an update; without the restart permission, the customer reopens the app after an update.

**Download checks:** HTTPS only, from `github.com`, its release download hosts, or `youriptv.us`.
If the GitHub APK download is unavailable, 0.5.6 can try the versioned APK on the website using
the selected release's size and SHA-256; it cannot substitute a different release. Checks include
size, SHA-256 from `update.json`, package name, version code, signing certificate, app label, minSdk,
and an **identical requested-permission set**. Any release that adds or removes a permission is
rejected, so a permission change always needs a technician install.

**Publishing a release** (in `youriptvus/YourIPTV`):

1. Raise `versionCode`/`versionName` in `app/build.gradle.kts`, add its customer-facing notes to
   `InstalledChangelog.kt`, and build the signed release.
2. Run `py scripts/make_update_json.py --previous <immediately previous published versionCode>`.
   For 1.0.5 preparation use `--previous 35`, following 1.0.4 / code 35.
   For future releases use the immediately previous actually published code. Devices at that code may Install or Ignore;
   devices below it must update. Unpublished builds are not intermediate install requirements.
3. Create a published (non-draft, non-prerelease) release tagged `v<versionName>`. Attach two assets:
   **`YourIPTV.apk`** and **`update.json`**. The APK URL inside `update.json` must be
   `https://github.com/youriptvus/YourIPTV/releases/download/v<versionName>/YourIPTV.apk`.
4. Boxes read `https://github.com/youriptvus/YourIPTV/releases/latest/download/update.json`. This is not
   rate-limited like `api.github.com`, and no token or customer data is sent.
5. Generate a second manifest from the same signed APK with `--mirror --out update-website.json`.
   Upload `YourIPTV-<versionName>-release.apk` to the website's `/updates/` folder first, verify it,
   then upload the website manifest as `/updates/update.json`. Serve the manifest with
   `Cache-Control: no-cache`. See [docs/UPDATE_SERVER.md](docs/UPDATE_SERVER.md) for staging,
   validation and hosting details.

Testing builds can point at a local server:
`-PupdateTestOrigin=http://127.0.0.1:8765/ -PupdateTestVersionCode=N -PupdateTestVersionName=0.4.0-testN`.
To exercise fallback, configure a separate `-PupdateTestBackupOrigin=http://127.0.0.1:8765/backup/`.
Setting the primary test origin without a backup test origin disables the website backup for that
build, so a local test does not accidentally contact the production mirror.
Do not distribute those APKs. Automatic updates are excluded from backup imports.

See [docs/RELEASE_NOTES.md](docs/RELEASE_NOTES.md) for the changelog.

## Free-feature policy

**Every YourIPTV player feature is free for every user.** There is no YourIPTV account,
registration, purchase, subscription, activation code, license key, trial, paywall, or paid
feature lock. This includes playback, playlists, the EPG/guide, recording, catch-up, favorites,
parental controls, settings, and every other implemented feature. The automated test
`NoPremiumAuditTest` checks the sources, resources, manifest and dependencies for billing,
licensing, trial and paywall mechanisms.

### Paid offering: custom rebranding only

**All player features are free; custom rebranding is paid.** Custom rebranding is the **only** paid
offering associated with YourIPTV. A company or customer can receive a customized build with its own
app name, logos and branding images, phone launcher icons, Android TV icon/banner, and About-screen
company and support/contact details.

- Rebranding work is held in a **separate, authorized rebranding tool** outside this repository.
  Its unfinished companion integration remains paused and is excluded from 1.0.5. The app itself
  contains no payment screens, "Unlock Premium" buttons, or branding controls.
- A rebranded app is **never** more capable than the standard app, and the standard app is never
  reduced. Payment covers the custom branding only. It never unlocks, limits, or restricts any player
  feature, including playback, playlists, Xtream Codes, M3U, the EPG/guide, recording, catch-up,
  favorites, parental controls, or settings.
- Rebranding never touches player behavior, user settings, playlists, provider credentials or
  security controls, and the free-use notice and open-source license acknowledgements stay on the
  About screen.
- No other premium tier, subscription, or paid feature restriction exists or is planned.
- This repository contains no billing, subscription, licensing-server, or activation code. It contains
  only the centralized default branding (`res/values/branding.xml`, `ui/Branding.kt`, launcher icons and
  TV banner) that the separate tool will customize. See [docs/BRANDING.md](docs/BRANDING.md).

### What still applies

These are outside YourIPTV's control or are security features. None of them is a premium
restriction.

- **Provider account:** you need a playlist URL or Xtream Codes login from an IPTV provider. Its
  account status, expiry date, and content are set by that provider.
- **Provider connection limits:** many providers allow only one or a few simultaneous streams
  (`max_connections`). Previews, full-screen playback and recordings each use a connection.
  YourIPTV explains limit-related errors (for example HTTP 401/403/509), but it cannot raise the
  limit.
- **Android and hardware limits:** codec support, decoder performance, display refresh modes,
  picture-in-picture availability, storage space, and system key handling depend on the device.
- **Parental controls and Settings protection:** these are separate security controls.
  - The parental PIN protects locked groups, adult categories, protected playback and,
    optionally, playlist management.
  - Settings protection is always enabled. The permanent master and any preserved or optional
    custom Settings PIN can open Settings; only the master can add, change or remove a custom PIN.
    New custom Settings PINs must be different from the parental PIN.
  - Master and custom Settings attempts share persistent failed-attempt limits. The master cannot
    bypass a Settings lockout. Parental counters and restrictions remain independent.
  - The master offers intentional Settings recovery; it never overrides parental checks. Legacy
    shared-parental access to Settings ends in 1.0.1. There is no in-app parental PIN recovery.
  - About is available from the main menu without a PIN and does not unlock Settings. See
    [the complete Settings policy](docs/SETTINGS_SECURITY.md).

## Features

All of the following are implemented. Their device-test status is recorded in
[docs/REQUIREMENTS_CHECKLIST.md](docs/REQUIREMENTS_CHECKLIST.md) and
[docs/QA_CHECKLIST.md](docs/QA_CHECKLIST.md).

**Playlists and providers**

- **Xtream Codes login** (server, username, password), with account information: status, expiry
  and maximum connections.
- **YourIPTV login shortcut:** enter only your provider username and password; the server
  `http://cw-25.com` and playlist name YourIPTV are preset. Saved connections use normal Xtream
  storage and protected editing; an authorized working provider account is required.
- **M3U / M3U Plus by URL**, using the `tvg-id`, `tvg-name`, `tvg-logo`, `group-title`,
  `tvg-type`, `#EXTGRP` and `url-tvg` attributes. Large lists are imported in a stream into
  SQLite (tested with about 147,000 entries).
- **Multiple playlists:** add, edit, delete, refresh and switch the active playlist. A `get.php`
  link can be imported as Xtream or kept as M3U. **Settings → Playlists → Manage playlists** and
  its editor share one protected visit; the visible Delete action includes selection/confirmation.
- **Password visibility:** Show password / Hide password in the playlist editor. The password
  starts masked; leaving or switching setup modes masks it again without changing its text.
- **Stream options:** a live stream format option for Xtream (MPEG-TS or HLS), plus a per-playlist
  and a global custom User-Agent.

**Live TV and guide**

- **One combined flow:** main menu → TV → categories (Favorites, All channels, provider groups) →
  the category's guide.
- **Live preview:**
  - The first OK (or tap) on a channel plays it in a preview while the guide stays usable.
  - Selecting the same channel again opens full screen. This is not a timed double-click.
  - Moving focus alone never tunes a channel.
- **Back navigation:** Back steps one level at a time: full screen → guide → categories → main
  menu. The channel, category and position are restored, and the preview keeps playing.
- **Guide grid:** channel × timeline grid with a current-time marker and programme details. Hold
  LEFT to browse past programmes; RIGHT moves later.
- **XMLTV EPG:** from Xtream `xmltv.php`, the M3U `url-tvg` attribute, or a manual URL.
  - Channels are matched by `tvg-id` and by name.
  - Settings cover the time shift and how many past and future days to keep.
- **Catch-up / archive:**
  - Xtream `timeshift`.
  - M3U `catchup` / `catchup-source` attributes.
  - Archive availability for Xtream-panel M3U links.
  - Only on channels where the provider keeps an archive.
- **Reminders and recording:** programme reminders, and recording (record now, or scheduled from
  the guide) to app storage with a Recordings section.
- **Stream errors:** reconnection, plus clear messages for network, filtered-network, access/limit,
  unavailable and provider errors.

**Playback** (Media3 ExoPlayer: HLS and MPEG-TS)

- **Remote controls:** channel zapping with UP/DOWN and number entry, an info banner, a
  channel-list overlay, and player options.
- **Track and display options:**
  - audio track, subtitles, video track and info;
  - subtitles off by default on a new player, with manual subtitle On/Off selection retained;
  - aspect ratio;
  - picture-in-picture where the device supports it;
  - an external player.
- **Tuning:** buffer size, "prefer software video decoding", audio delay (PCM audio only), and an
  optional auto frame rate.
- **Movies and series (VOD):** browse categories with large portrait posters and a selected-title
  header. A wide TV display targets around 7–8 posters per row; short phone windows use a compact
  header and height-aware poster sizing so images and captions fit together. Opening a movie shows its available details before Play. Series show vertically stacked
  seasons with horizontal episode cards and remembered selections. Existing seek, resume, viewing
  history, favorites and internal/external playback remain available. See
  [the navigation and artwork guide](docs/VOD_NAVIGATION.md).
- **Start-up:** optionally start on the last channel watched.

**Library, safety and settings**

- **Library:** favorites, viewing history and search.
- **Parental controls:**
  - parental PIN and locked groups;
  - blocking of adult categories across the playlist;
  - an optional PIN for playlist management;
  - permanent Settings protection with a fixed master and optional custom Settings PIN.
- **Settings:** grouped into categories: General, Playlists, TV Guide / EPG, Playback, Appearance,
  Remote Controls, Parental Controls, Settings Lock, Backup & Restore, and About. About is also
  directly accessible from the main menu without opening Settings.
- **Local backup and restore** of playlists, favorites and settings, saved as a JSON file in the
  app's folder.
  - Backups contain playlist logins but never PINs. Treat backup files as private.

### Not implemented or deferred

- **Stalker portal:** deferred, because no test portal is available.
- **Multiview:** deferred, because the authorized test provider allows only one connection. If
  it is added, it will be free.
- **Background reminders and recordings:** reminders and scheduled recordings run only while
  YourIPTV is running. By design there is no background service, alarm, notification or boot
  permission.
- **In-app parental PIN recovery:** not provided. Settings access can use the permanent master,
  subject to the normal Settings lockout.
- **Company branding:** a possible future offering only (see above). Not implemented.

## Device support

### Android TV and remote control

- The app appears in the Leanback launcher with a TV banner; a touchscreen is not required.
- Every screen works with the D-pad, OK/Select and Back, and some screens also use MENU,
  long-press OK, digits, CH+/CH−, FF/REW and media keys.
- Hold **Back** for about 0.6 seconds in the app or guide to return to the current stream in full
  screen. It follows the channel actually watched rather than the highlighted guide row. Short Back
  presses keep their usual action; parental restrictions still apply.
- Every focusable element shows a visible focus highlight.
- Pressing Back on the main menu asks you to press Back again before the app closes.
- Google TV intercepts the remote's dedicated **GUIDE** key, so it opens the system's own guide.
  Open the guide from **TV** in the main menu, or from **player options → TV guide**.
- The complete key map is in [docs/REMOTE_CONTROLS.md](docs/REMOTE_CONTROLS.md).

### Phones

- Touch input, a portrait layout and full-screen landscape playback are implemented.
  - Tapping a channel row selects it with the same preview rules as OK.
  - In full screen, tapping toggles the playback banner.
- Live playback on a moto g 2025 was device-tested for version 0.3.3: preview, full screen,
  showing and hiding the system bars, rotation, background/return, channel changes and a
  10-minute run.
- The full phone UI has not been systematically tested, unlike the TV boxes. Treat phone layouts
  outside playback as less verified.

### Permissions

The app requests network access and the existing Android install/restart permissions used by
automatic updates. 1.0.5 adds or removes no requested permissions. It declares no services,
broadcast receivers or content providers, and no HOME launcher category. The automated test
`StandaloneAppAuditTest` checks that the player remains a standalone app.

## Setting up a provider

Use only a provider and playlist you are authorized to access. Credentials are stored locally on
the device in the app's private database. Do not commit them to this repository.

Open **Playlists** in the main menu and add a playlist. Choose **M3U playlist URL**,
**Xtream Codes login**, or **YourIPTV**. M3U and generic Xtream setup retain optional EPG URL
and custom User-Agent fields. The YourIPTV shortcut uses the provider's normal guide and
default User-Agent without requesting those fields.

To edit the active playlist, choose **Playlists → Edit current** or
**Settings → Playlists → Edit current playlist**. **Settings → Playlists → Manage playlists**
opens the full manager for refresh, editing and deletion. Unlocking Settings once covers its
authorized manager/editor navigation during the same protected visit. Direct editing from
outside that flow still requires Settings authentication before connection details are shown
or saved. Leaving the flow, Home/background, screen-off or recreation relocks it. The independent
parental check still applies when playlist locking is enabled; this is not a global unlock.

Edit its URL, Xtream username/password, name, guide URL or User-Agent, then choose **Save and load**.
Editing another playlist remains available through its MENU or long-press options. Passwords
are masked initially; **Show password / Hide password** preserves exact text and cursor position.
They are masked again on leaving/background or changing setup mode; reveal state is not restored.

To remove a playlist, use visible **Delete playlist**, choose the playlist, then read its named
confirmation. **Cancel** leaves it intact. Deletion requires Settings authorization and applicable
parental permission, and is disabled during refresh. It removes that playlist's catalog, guide,
favorites, history, reminders and pending recording entries; completed recording files remain,
with retained stream URLs cleared. Other playlists remain. Deleting the active playlist selects
a remaining playlist or shows an empty list. Owner-device tests must cancel deletion rather than
remove real saved accounts.

Changes to the playlist source, login, or guide URL start a reload. If loading fails, the new
settings remain saved; correct them and choose **Save** again, or refresh later from Playlists.
Changing providers or credentials can change stream URLs, so favorites or history tied to the old
URLs may not match the refreshed list.

### YourIPTV shortcut

1. Choose **YourIPTV**, the third option in Add playlist.
2. Enter the **Username** and **Password** for your authorized account on **http://cw-25.com**.
   The password is masked; do not share it in screenshots or issue reports.
3. Choose **Save and load**. The app names the playlist **YourIPTV**, authenticates using the
   existing Xtream process, and loads the provider's available channels, movies, shows and guide.

This option supplies no account or subscription. Provider account status, expiry and connection
limits still apply. It saves an ordinary Xtream playlist, with no new database type or migration.
Later **Edit** opens the full Xtream editor after the usual Settings and applicable parental
authorization. Existing connections are never automatically renamed or converted. If a newly
added connection fails, an existing working active playlist remains active; correct the saved
login and retry using the existing editing flow.

### Xtream Codes

| Field | Example (placeholder) |
|---|---|
| Server | `http://provider.example:8080` |
| Username | `YOUR_USERNAME` |
| Password | `YOUR_PASSWORD` |

The EPG is loaded automatically from the panel's `xmltv.php`. The live stream format (MPEG-TS or
HLS) is under **Settings → Playback**.

### M3U / M3U Plus

Enter the playlist URL, for example:

```text
http://provider.example:8080/get.php?username=YOUR_USERNAME&password=YOUR_PASSWORD&type=m3u_plus
```

When you add a `get.php` link, YourIPTV asks whether to import it as Xtream or keep it as M3U.

### EPG (XMLTV)

Each playlist has an optional **EPG URL** field, for example `http://epg.example/guide.xml`.
YourIPTV chooses the guide source in this order:

1. The playlist's EPG URL. If you leave it blank for an M3U playlist, it is filled in from the
   playlist header's `url-tvg` attribute when the playlist loads.
2. For Xtream playlists, the panel's `xmltv.php`.
3. For an M3U playlist whose URL is an Xtream-style `get.php` login, the `xmltv.php` address
   derived from that URL.

Guide coverage depends on the provider's `tvg-id` / EPG IDs: channels that cannot be matched show
no programme information. Guide time shift, retention and update options are under
**Settings → TV Guide / EPG**.

## Building from a fresh clone (Windows)

### Prerequisites

| Requirement | Version used by this project |
|---|---|
| Git for Windows | any recent version |
| JDK | **17 or newer**. The code targets Java 17; the local builds used JDK 27.0.1 |
| Android SDK | **Platform 36** (`platforms;android-36`) and **Build-Tools 36.0.0** |
| Gradle | 9.8.1, downloaded automatically by the wrapper. Do not install it separately |
| Android Gradle Plugin | 9.5.0, with its built-in Kotlin (2.4.10) |
| Internet access | needed for the first build, to download Gradle and dependencies from Google Maven and Maven Central |

The easiest way to get the SDK is to install
[Android Studio](https://developer.android.com/studio) and add **Android SDK Platform 36** and
**Build-Tools 36.0.0** in its SDK Manager. Alternatively, use the command-line tools:

```powershell
sdkmanager "platforms;android-36" "build-tools;36.0.0" "platform-tools"
```

### Steps

```powershell
git clone https://github.com/youriptvus/YourIPTV.git
cd YourIPTV

# Tell Gradle where the Android SDK is. Use ONE of these:
#   a) an environment variable for this session
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
#   b) or a local.properties file (git-ignored), e.g.
#      sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk

# If the default java is older than 17, point JAVA_HOME at a JDK 17+ install, e.g.
# $env:JAVA_HOME = "C:\Program Files\Java\jdk-17"

.\gradlew.bat assembleDebug
```

### Build commands and outputs

| Command | Output |
|---|---|
| `.\gradlew.bat assembleDebug` | `app\build\outputs\apk\debug\app-debug.apk` (signed with the local debug key) |
| `.\gradlew.bat assembleRelease` with `keystore.properties` | `app\build\outputs\apk\release\app-release.apk` (signed release) |
| `.\gradlew.bat assembleRelease` without `keystore.properties` | `app\build\outputs\apk\release\app-release-unsigned.apk` (cannot be installed until signed) |
| `.\gradlew.bat testDebugUnitTest` | JVM unit tests; HTML report in `app\build\reports\tests\testDebugUnitTest\index.html` |

Generated APKs are git-ignored. Do not commit them.

### Release signing

1. Copy `keystore.properties.example` to `keystore.properties` in the project root. This file is
   git-ignored.
2. Fill in `storeFile`, `storePassword`, `keyAlias` and `keyPassword`. `storeFile` is resolved
   relative to the `app\` folder; an absolute path also works.
3. Keep the keystore **outside** the repository, and back it up.

Android installs an update over an existing installation only if it is signed with the same key.
A build signed with a different key (including a debug build) cannot replace an installed
release build without uninstalling it, and uninstalling deletes the user's playlists and
settings. Never commit `keystore.properties`, `*.jks` or `*.keystore` files; `.gitignore`
excludes them.

## Installing on a device

The current project targets **1.0.5 (version code 36)**. Build and verify the matching signed APK
before distributing it. A compatible official update retains app data; no publication is implied.
The higher-version in-app installation path has not yet been tested for this release.

1. Enable **Developer options → USB debugging** (or **Wireless/network debugging** on Android
   TV).
2. Connect, then install or update while keeping the app's data:

```powershell
# For network debugging on a TV, connect without USB first:
adb connect <device-ip:port>
adb devices
adb -s <device-serial-or-ip:port> install -r app\build\outputs\apk\release\app-release.apk
```

Always update with `install -r`. Do not uninstall the app or clear its data to "fix" an
install: that removes playlists, favorites, settings and backups stored in the app's folder.
You can also copy the APK to the device and open it with a file manager that is allowed to
install apps.

## Testing

```powershell
.\gradlew.bat testDebugUnitTest
```

- Final 1.0.5 evidence: **324 tests in 44 classes**, zero failures/errors/skips, run
  `20261112T002351Z-939036f5`; signed release assembly/vital lint, native/compiled APK checks,
  15 archived updater checks, 21 retained-APK identity audits and three actual-SQL deletion
  fixtures passed. The initial run's obsolete Main routing assertion was corrected to verify
  the manager's parental gate; its failed reports remain preserved. Scoped TV checks passed
  on the exact APK, using existing provider data and deletion Cancel only. Phone, destructive
  owner-data tests, fresh parental-configuration changes, VOD/Series playback and actual automatic
  download/install/popup checks were not performed. See [the QA record](docs/QA_CHECKLIST.md).
- Historical final 1.0.4 automated evidence: **300 tests in 41 classes**, zero failures/errors/skips, fresh run
  `20261111T232602Z-1ecb7113` (1m38s). Coverage includes the fixed shortcut endpoint/name/type,
  credential validation/encoding, hidden-field isolation, existing editor protections and receipt/
  exact-note credit behavior. Signed assembly/vital lint passed (4m22s), along with native/compiled
  APK checks, 14 archived updater checks and 20 retained-APK identity audits. The initial suite's
  one branding-centralization failure was corrected before this fresh full pass; failed reports
  remain preserved. TV connection was unavailable and the owner excluded phone testing. No real
  shortcut login, UI/playback, on-device installation or automatic-update popup pass is claimed.
  See the [QA record](docs/QA_CHECKLIST.md) for the exact APK, scope and remaining checks.
- Historical final 1.0.3 evidence: **288 tests in 40 classes**, zero failures/errors/skips; signed release
  assembly/release vital lint, native verification, 13 archived updater fixtures and 19 retained-APK
  identity audits passed. Scoped final-APK phone/TV tests passed, including the corrected Series
  return focus. Real automatic download/install and its popup, portrait/global inset behavior,
  independent seek accuracy, network-failure injection and long live-playback soak were not tested.
  See [docs/QA_CHECKLIST.md](docs/QA_CHECKLIST.md) for exact evidence and limits; staging status is
  recorded separately in the post-commit publication instructions and final audit.
- Historical 1.0.2 evidence: **275 tests in 40 classes** passed with zero failures/errors/skips.
  Signed release assembly, release vital lint, native APK checks and 12 archived updater source
  checks passed. Scoped regression checks on the exact final APK passed on an onn. 4K Pro using
  the existing authorized playlist. The backup website deployment passed independent HTTPS
  verification. Later phone checks found the clipped grid described above; movie video/resume
  on that phone remained unconfirmed. These historical results do not certify 1.0.5. Device/update-
  path limits belong in [docs/QA_CHECKLIST.md](docs/QA_CHECKLIST.md).
  Sources are in
  `app\src\test\java\com\youriptv\app`.
  They cover:
  - the M3U and XMLTV parsers, Xtream URLs and catch-up URL building;
  - the connection-limit logic;
  - parental-control and PIN logic;
  - guide navigation and preview-selection rules;
  - VOD grid sizing, navigation/restoration, season/episode ordering and optional artwork;
  - live-stream recovery and the MPEG-TS access-unit handling;
  - backup and restore;
  - the no-premium and standalone-app audits.
- Optional Android Lint: `.\gradlew.bat lintDebug` (report in `app\build\reports\`).

Limitations of automated testing:

- There are no instrumented (on-device) or UI tests. Navigation, focus, playback, provider
  behaviour and device-specific decoding require real-device testing. Historical manual results
  and their limits are recorded in [docs/QA_CHECKLIST.md](docs/QA_CHECKLIST.md) and
  [docs/KNOWN_ISSUES.md](docs/KNOWN_ISSUES.md).
- The tests use no network and no real provider. Optional manual-test settings belong in a
  local, git-ignored `test-config.local.properties` (see `test-config.local.properties.example`).
  Neither the build nor the unit tests read it.
- Continuous integration (GitHub Actions) is **not set up yet** in this repository.

## Project structure

```text
YourIPTV/
├── app/
│   ├── build.gradle.kts            App module: version, SDK levels, signing, dependencies
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/youriptv/app/
│       │   │   ├── YourIptvApp.kt  Application object (repository and settings)
│       │   │   ├── data/           Models, SQLite database, repository, M3U/XMLTV parsers,
│       │   │   │                   Xtream URLs, catch-up, recorder, parental controls,
│       │   │   │                   backup, HTTP, connection limits, stream diagnosis
│       │   │   └── ui/             Activities (main/TV, player, playlists, settings, series,
│       │   │                       about), live guide grid, preview panel and live playback
│       │   │                       session, player factory and MPEG-TS extractors,
│       │   │                       audio delay, auto frame rate, scheduler
│       │   └── res/                Layouts (TV and portrait), drawables, icons, TV banner, strings
│       └── test/java/com/youriptv/app/   JVM unit tests
├── docs/                           Requirements, release notes, test report, QA checklist,
│                                   remote controls, known issues, TiviMate reference notes,
│                                   branding source image
├── gradle/wrapper/                 Gradle wrapper JAR and properties (9.8.1)
├── build.gradle.kts                Root build (Android Gradle Plugin 9.5.0)
├── settings.gradle.kts             Repositories and module list
├── gradle.properties               Gradle JVM and AndroidX settings
├── gradlew, gradlew.bat            Gradle wrapper scripts
├── keystore.properties.example     Template for release signing (copy; never commit the real file)
└── test-config.local.properties.example   Template for optional manual-test settings
```

### Local-only files (git-ignored)

| File | Purpose |
|---|---|
| `local.properties` | `sdk.dir` path to your Android SDK (machine-specific) |
| `keystore.properties` | Release signing values |
| `test-config.local.properties` | Authorized test provider and test PINs for manual device testing |
| `build/`, `.gradle/`, `.kotlin/` | Build output and caches |

## Troubleshooting

| Symptom | What to check |
|---|---|
| `SDK location not found` | Set `ANDROID_HOME` or create `local.properties` with `sdk.dir=…` |
| `Failed to find target android-36` or Build-Tools errors | Install Platform 36 and Build-Tools 36.0.0 in the SDK Manager |
| Gradle reports an unsupported Java version | Use JDK 17 or newer and set `JAVA_HOME` |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | The APK is signed with a different key from the installed app. Build with the original release key; uninstalling would delete user data |
| Release APK is `app-release-unsigned.apk` | `keystore.properties` is missing; see [Release signing](#release-signing) |
| Playback stops or fails with an access/limit message | The provider's connection limit is reached. Stop other streams or recordings on the same account |
| Catch-up shows a network-filter / block message | Some ISPs or networks block the provider's archive host. YourIPTV reports this; it includes no VPN |
| Guide shows "No programme information" | The channel has no `tvg-id` or no EPG match; check the provider's EPG |
| Choppy video with "Prefer software video decoding" on | Software decoding is CPU-limited on some phones at 1080p60. Use the default hardware decoding |
| GUIDE key opens Google TV instead of YourIPTV | Expected on Google TV; open the guide from TV in the main menu |

## Known issues

These are the main ones; the full, current list is in [docs/KNOWN_ISSUES.md](docs/KNOWN_ISSUES.md).

- Google TV intercepts the remote's GUIDE key.
- Reminders and scheduled recordings work only while the app is running.
- A recording uses a provider connection, so with a one-connection provider you cannot watch
  another channel at the same time.
- Auto frame rate cannot be observed on displays that offer only one refresh mode.
- Backups are stored in the app's folder and are deleted when the app is uninstalled.
- "Prefer software video decoding" can stutter on lower-powered phones at 1080p60.
- On phones, relaunching through a bare launcher intent (for example `adb shell monkey`) while in
  landscape full screen returns to the categories without playback. The real launcher icon and
  Recents resume correctly.

## Documentation

| Document | Contents |
|---|---|
| [docs/RELEASE_NOTES.md](docs/RELEASE_NOTES.md) | Changelog through 1.0.5 |
| [docs/VOD_NAVIGATION.md](docs/VOD_NAVIGATION.md) | Movies/Shows posters, movie details, season rows, artwork and verification limits |
| [docs/SETTINGS_SECURITY.md](docs/SETTINGS_SECURITY.md) | Always-on Settings protection, custom PINs and migration |
| [docs/UPGRADING_TO_1_0.md](docs/UPGRADING_TO_1_0.md) | Direct upgrades, older-version exceptions and publication |
| [docs/REQUIREMENTS_CHECKLIST.md](docs/REQUIREMENTS_CHECKLIST.md) | Requirements and implementation status |
| [docs/QA_CHECKLIST.md](docs/QA_CHECKLIST.md) | Full hands-on QA pass |
| [docs/REMOTE_CONTROLS.md](docs/REMOTE_CONTROLS.md) | Remote-control key map |
| [docs/KNOWN_ISSUES.md](docs/KNOWN_ISSUES.md) | Known issues and test limitations |
| [docs/TIVIMATE_OBSERVATIONS.md](docs/TIVIMATE_OBSERVATIONS.md), [docs/LIVE_PREVIEW_REFERENCE.md](docs/LIVE_PREVIEW_REFERENCE.md) | Observed reference behaviour used for the interface design. YourIPTV is an independent implementation and reuses no TiviMate code, assets or branding |

## License

No license has been chosen for this source code yet, and no license file is included. Until the
owner adds one, no permission to copy, modify or redistribute the code is granted. The free-feature
policy above describes the app's features; it is not a license to reuse the source code.
