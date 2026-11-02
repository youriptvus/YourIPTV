# YourIPTV

YourIPTV is an independent IPTV player for **Android TV** and **Android phones**, written in Kotlin
with [Media3 ExoPlayer](https://developer.android.com/media/media3/exoplayer). You add your own
**Xtream Codes** or **M3U / M3U Plus** playlist and an **XMLTV (EPG)** guide from a provider you
are authorized to use. YourIPTV contains no channels, streams, or provider accounts of its own.

YourIPTV is an app only. It is not an Android launcher, a home-screen replacement, an OS
modification, or a device-management tool. It works the same whether it is opened directly or
from a separate launcher.

## Development status

| | |
|---|---|
| Current version | **0.3.3** (`versionCode` 7), application ID `com.youriptv.app` |
| Stage | Pre-1.0, under active development and testing |
| Distribution | Side-loaded APK. No Google Play listing and no published GitHub Release yet |
| Supported Android | `minSdk` 23 (Android 6.0) to `targetSdk` 36 |
| Main test devices | onn. Streaming Device 4K Pro (Google TV); onn. Full HD Streaming Device; moto g 2025 phone (playback) |

See [docs/RELEASE_NOTES.md](docs/RELEASE_NOTES.md) for the changelog, and
[docs/TEST_REPORT.md](docs/TEST_REPORT.md) for the tests that were actually run on devices.

## Free-feature policy

**Every YourIPTV player feature is free for every user.** There is no YourIPTV account,
registration, purchase, subscription, activation code, license key, trial, paywall, or paid
feature lock. This includes playback, playlists, the EPG/guide, recording, catch-up, favorites,
parental controls, settings, and every other implemented feature. The automated test
`NoPremiumAuditTest` checks the sources, resources, manifest and dependencies for billing,
licensing, trial and paywall mechanisms.

### Possible future offering: company branding

The **only** paid offering under consideration is optional **company branding**. This would let a
business replace the "Your IPTV" name and logo with its own company name and logo.

- It is a **possibility only**. It is not available, not implemented, and not committed to. No
  pricing or release date has been set.
- Any payment would cover the custom name and logo only. It would **never** unlock, limit, or
  restrict any player feature, including playback, playlists, the EPG/guide, recording,
  catch-up, favorites, parental controls, or settings.
- No other premium tier, subscription, or paid feature restriction is planned.
- This repository contains no billing, subscription, licensing-server, activation, premium-screen
  or rebranding code.

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
- **Parental-control PIN and Settings-lock PIN:** these are two separate, independent security
  controls, and the two PINs must be different.
  - The parental PIN protects locked groups, adult categories, protected playback and,
    optionally, playlist management.
  - The Settings PIN protects the Settings screens.
  - Wrong entries are throttled.
  - There is no in-app PIN recovery. Resetting a forgotten PIN requires clearing the app's data,
    which also removes its playlists.

## Features

All of the following are implemented. Their device-test status is recorded in
[docs/REQUIREMENTS_CHECKLIST.md](docs/REQUIREMENTS_CHECKLIST.md) and
[docs/TEST_REPORT.md](docs/TEST_REPORT.md).

**Playlists and providers**

- **Xtream Codes login** (server, username, password), with account information: status, expiry
  and maximum connections.
- **M3U / M3U Plus by URL**, using the `tvg-id`, `tvg-name`, `tvg-logo`, `group-title`,
  `tvg-type`, `#EXTGRP` and `url-tvg` attributes. Large lists are imported in a stream into
  SQLite (tested with about 147,000 entries).
- **Multiple playlists:** add, edit, delete, refresh and switch the active playlist. A `get.php`
  link can be imported as Xtream or kept as M3U.
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
  - aspect ratio;
  - picture-in-picture where the device supports it;
  - an external player.
- **Tuning:** buffer size, "prefer software video decoding", audio delay (PCM audio only), and an
  optional auto frame rate.
- **Movies and series (VOD):** browse by category, seasons and episodes, seek, resume, and viewing
  history.
- **Start-up:** optionally start on the last channel watched.

**Library, safety and settings**

- **Library:** favorites, viewing history and search.
- **Parental controls:**
  - parental PIN and locked groups;
  - blocking of adult categories across the playlist;
  - an optional PIN for playlist management;
  - a separate Settings lock with its own PIN.
- **Settings:** grouped into categories: General, Playlists, TV Guide / EPG, Playback, Appearance,
  Remote Controls, Parental Controls, Settings Lock, Backup & Restore, and About.
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
- **In-app PIN recovery:** not provided.
- **Company branding:** a possible future offering only (see above). Not implemented.

## Device support

### Android TV and remote control

- The app appears in the Leanback launcher with a TV banner; a touchscreen is not required.
- Every screen works with the D-pad, OK/Select and Back, and some screens also use MENU,
  long-press OK, digits, CH+/CH−, FF/REW and media keys.
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

The only permissions are `INTERNET` and `ACCESS_NETWORK_STATE`. The app declares no services,
broadcast receivers or content providers, and no HOME launcher category. The automated test
`StandaloneAppAuditTest` enforces this.

## Setting up a provider

Use only a provider and playlist you are authorized to access. Credentials are stored locally on
the device in the app's private database. Do not commit them to this repository.

Open **Playlists** in the main menu and add a playlist. The EPG URL and a custom User-Agent are
optional fields on the same screen.

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
git clone https://github.com/comp6062/YourIPTV.git
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

1. Enable **Developer options → USB debugging** (or **Wireless/network debugging** on Android
   TV).
2. Connect, then install or update while keeping the app's data:

```powershell
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

- The suite has **127 JVM unit tests in 20 test classes** (`app\src\test\java\com\youriptv\app`).
  They cover:
  - the M3U and XMLTV parsers, Xtream URLs and catch-up URL building;
  - the connection-limit logic;
  - parental-control and PIN logic;
  - guide navigation and preview-selection rules;
  - live-stream recovery and the MPEG-TS access-unit handling;
  - backup and restore;
  - the no-premium and standalone-app audits.
- Optional Android Lint: `.\gradlew.bat lintDebug` (report in `app\build\reports\`).

Limitations of automated testing:

- There are no instrumented (on-device) or UI tests. Navigation, focus, playback, provider
  behaviour and device-specific decoding were verified **by hand on real devices**. Those results
  and their limits are recorded in [docs/TEST_REPORT.md](docs/TEST_REPORT.md) and
  [docs/QA_CHECKLIST.md](docs/QA_CHECKLIST.md).
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
| [docs/RELEASE_NOTES.md](docs/RELEASE_NOTES.md) | Changelog by version (0.1.1 → 0.3.3) |
| [docs/REQUIREMENTS_CHECKLIST.md](docs/REQUIREMENTS_CHECKLIST.md) | Requirements and implementation status |
| [docs/TEST_REPORT.md](docs/TEST_REPORT.md) | Device tests actually performed, with results |
| [docs/QA_CHECKLIST.md](docs/QA_CHECKLIST.md) | Full hands-on QA pass |
| [docs/REMOTE_CONTROLS.md](docs/REMOTE_CONTROLS.md) | Remote-control key map |
| [docs/KNOWN_ISSUES.md](docs/KNOWN_ISSUES.md) | Known issues and test limitations |
| [docs/TIVIMATE_OBSERVATIONS.md](docs/TIVIMATE_OBSERVATIONS.md), [docs/LIVE_PREVIEW_REFERENCE.md](docs/LIVE_PREVIEW_REFERENCE.md) | Observed reference behaviour used for the interface design. YourIPTV is an independent implementation and reuses no TiviMate code, assets or branding |

## License

No license has been chosen for this source code yet, and no license file is included. Until the
owner adds one, no permission to copy, modify or redistribute the code is granted. The free-feature
policy above describes the app's features; it is not a license to reuse the source code.
